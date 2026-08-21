package com.motodigo.core.geo.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.motodigo.core.geo.config.GeoStackProperties;
import com.motodigo.core.urban.dto.UrbanPlaceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PhotonClient {

	private static final Logger log = LoggerFactory.getLogger(PhotonClient.class);

	private final GeoStackProperties properties;
	private final RestClient restClient;

	@Autowired
	public PhotonClient(GeoStackProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofMillis(Math.max(200, properties.photonTimeoutMs())));
		factory.setReadTimeout(Duration.ofMillis(Math.max(200, properties.photonTimeoutMs())));
		this.restClient = RestClient.builder()
				.baseUrl(properties.photonBaseUrl())
				.requestFactory(factory)
				.build();
	}

	PhotonClient(GeoStackProperties properties, RestClient restClient) {
		this.properties = properties;
		this.restClient = restClient;
	}

	static final String CAMEROON_BBOX = "8.45,1.65,16.20,13.08";
	/** ~0.35° ≈ 35–40 km around a departure city for pickup / POI search. */
	static final double LOCALITY_BIAS_DEG = 0.35;

	public List<UrbanPlaceResponse> search(String query, Double lat, Double lon, int limit) {
		return search(query, lat, lon, limit, null);
	}

	public List<UrbanPlaceResponse> search(String query, Double lat, Double lon, int limit, String kind) {
		if (!this.properties.photonReady() || query == null || query.isBlank()) {
			return List.of();
		}
		boolean city = isCityKind(kind);
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = this.restClient.get()
					.uri(uri -> {
						var builder = uri.path("/api")
								.queryParam("q", query)
								.queryParam("limit", Math.max(1, Math.min(limit, 15)))
								.queryParam("lang", "fr");
						if (city) {
							builder.queryParam("bbox", CAMEROON_BBOX)
									.queryParam("osm_tag", "place:city", "place:town", "place:village");
						}
						else {
							builder.queryParam("bbox", localityBbox(lat, lon));
							if (lat != null && lon != null) {
								builder.queryParam("lat", lat).queryParam("lon", lon);
							}
						}
						return builder.build();
					})
					.retrieve()
					.body(Map.class);
			List<UrbanPlaceResponse> places = toResponses(parseFeatures(body));
			if (city) {
				return places.stream()
						.filter(place -> "CITY".equalsIgnoreCase(place.kind())
								|| "LOCALITY".equalsIgnoreCase(place.kind()))
						.toList();
			}
			return places;
		}
		catch (RuntimeException ex) {
			log.warn("Photon search unavailable: {}", ex.getMessage());
			return List.of();
		}
	}

	/**
	 * Prefer a tight bbox around the departure city when bias is known; otherwise all of Cameroon
	 * (never Yaoundé-only for non-CITY pickup / POI search).
	 */
	static String localityBbox(Double lat, Double lon) {
		if (lat == null || lon == null) {
			return CAMEROON_BBOX;
		}
		double minLon = lon - LOCALITY_BIAS_DEG;
		double minLat = lat - LOCALITY_BIAS_DEG;
		double maxLon = lon + LOCALITY_BIAS_DEG;
		double maxLat = lat + LOCALITY_BIAS_DEG;
		return String.format(java.util.Locale.US, "%.4f,%.4f,%.4f,%.4f", minLon, minLat, maxLon, maxLat);
	}

	public boolean isReachable() {
		if (!this.properties.photonReady()) {
			return false;
		}
		try {
			this.restClient.get().uri("/status").retrieve().toBodilessEntity();
			return true;
		}
		catch (RuntimeException statusEx) {
			try {
				this.restClient.get()
						.uri(uri -> uri.path("/api").queryParam("q", "yaounde").queryParam("limit", 1).build())
						.retrieve()
						.toBodilessEntity();
				return true;
			}
			catch (RuntimeException apiEx) {
				log.debug("Photon ping failed: {}", apiEx.getMessage());
				return false;
			}
		}
	}

	public UrbanPlaceResponse reverse(double lat, double lon) {
		if (!this.properties.photonReady()) {
			return null;
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = this.restClient.get()
					.uri(uri -> uri.path("/reverse")
							.queryParam("lat", lat)
							.queryParam("lon", lon)
							.queryParam("lang", "fr")
							.build())
					.retrieve()
					.body(Map.class);
			List<UrbanPlaceResponse> places = toResponses(parseFeatures(body));
			return places.isEmpty() ? null : places.getFirst();
		}
		catch (RuntimeException ex) {
			log.warn("Photon reverse unavailable: {}", ex.getMessage());
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	static List<PhotonPlace> parseFeatures(Map<String, Object> body) {
		List<PhotonPlace> places = new ArrayList<>();
		if (body == null || !(body.get("features") instanceof List<?> features)) {
			return places;
		}
		for (Object feature : features) {
			if (!(feature instanceof Map<?, ?> featureMap)) {
				continue;
			}
			Map<String, Object> geometry = featureMap.get("geometry") instanceof Map<?, ?> geom
					? (Map<String, Object>) geom
					: Map.of();
			Map<String, Object> properties = featureMap.get("properties") instanceof Map<?, ?> props
					? (Map<String, Object>) props
					: Map.of();
			List<?> coordinates = geometry.get("coordinates") instanceof List<?> list ? list : List.of();
			if (coordinates.size() < 2) {
				continue;
			}
			double lon = asDouble(coordinates.get(0));
			double lat = asDouble(coordinates.get(1));
			String name = asString(properties.get("name"));
			if (name == null || name.isBlank()) {
				name = asString(properties.get("street"));
			}
			if (name == null || name.isBlank()) {
				continue;
			}
			long osmId = properties.get("osm_id") instanceof Number number ? number.longValue() : 0L;
			places.add(new PhotonPlace(osmId, name, subtitle(properties), kind(properties), lat, lon));
		}
		return List.copyOf(places);
	}

	static List<UrbanPlaceResponse> toResponses(List<PhotonPlace> places) {
		List<UrbanPlaceResponse> responses = new ArrayList<>();
		for (PhotonPlace place : places) {
			UUID id = UUID.nameUUIDFromBytes(("photon:" + place.osmId() + ":" + place.name()).getBytes());
			responses.add(new UrbanPlaceResponse(id, place.name(), place.lat(), place.lon(), place.kind(),
					place.subtitle(), "PHOTON", null));
		}
		return List.copyOf(responses);
	}

	private static String subtitle(Map<String, Object> properties) {
		List<String> parts = new ArrayList<>();
		for (String key : List.of("osm_value", "city", "district", "county", "state")) {
			String value = asString(properties.get(key));
			if (value != null && !value.isBlank() && !parts.contains(value)) {
				parts.add(value);
			}
		}
		return parts.isEmpty() ? null : String.join(" · ", parts);
	}

	private static String kind(Map<String, Object> properties) {
		String osmKey = asString(properties.get("osm_key"));
		String osmValue = asString(properties.get("osm_value"));
		String type = asString(properties.get("type"));
		if ("highway".equalsIgnoreCase(osmKey) || "street".equalsIgnoreCase(type)) {
			return "STREET";
		}
		if ("city".equalsIgnoreCase(type) || "city".equalsIgnoreCase(osmValue) || "town".equalsIgnoreCase(osmValue)
				|| "village".equalsIgnoreCase(osmValue)) {
			return "CITY";
		}
		if ("suburb".equalsIgnoreCase(osmValue) || "neighbourhood".equalsIgnoreCase(osmValue)
				|| "neighborhood".equalsIgnoreCase(osmValue) || "district".equalsIgnoreCase(type)) {
			return "NEIGHBORHOOD";
		}
		if ("tourism".equalsIgnoreCase(osmKey)) {
			return "LANDMARK";
		}
		return "POI";
	}

	static boolean isCityKind(String kind) {
		return kind != null && "CITY".equalsIgnoreCase(kind.trim());
	}

	private static String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static double asDouble(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		return Double.parseDouble(String.valueOf(value));
	}
}
