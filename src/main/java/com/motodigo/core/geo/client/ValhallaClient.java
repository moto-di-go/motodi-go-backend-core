package com.motodigo.core.geo.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.motodigo.core.geo.codec.PolylineCodec;
import com.motodigo.core.geo.config.GeoStackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ValhallaClient {

	private static final Logger log = LoggerFactory.getLogger(ValhallaClient.class);

	private final GeoStackProperties properties;
	private final RestClient restClient;

	@Autowired
	public ValhallaClient(GeoStackProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofMillis(Math.max(200, properties.valhallaTimeoutMs())));
		factory.setReadTimeout(Duration.ofMillis(Math.max(200, properties.valhallaTimeoutMs())));
		this.restClient = RestClient.builder()
				.baseUrl(properties.valhallaBaseUrl())
				.requestFactory(factory)
				.build();
	}

	ValhallaClient(GeoStackProperties properties, RestClient restClient) {
		this.properties = properties;
		this.restClient = restClient;
	}

	public List<ValhallaPath> route(double originLat, double originLon, double destLat, double destLon) {
		if (!this.properties.valhallaReady()) {
			return List.of();
		}
		List<ValhallaPath> paths = routeWithCosting(originLat, originLon, destLat, destLon,
				this.properties.valhallaCosting());
		if (paths.isEmpty() && !"auto".equalsIgnoreCase(this.properties.valhallaCosting())) {
			paths = routeWithCosting(originLat, originLon, destLat, destLon, "auto");
		}
		return paths;
	}

	public ValhallaPath match(List<double[]> latLonPoints) {
		if (!this.properties.valhallaReady() || latLonPoints == null || latLonPoints.size() < 2) {
			return null;
		}
		try {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("shape", shape(latLonPoints));
			payload.put("costing", this.properties.valhallaCosting());
			payload.put("shape_match", "map_snap");
			@SuppressWarnings("unchecked")
			Map<String, Object> body = this.restClient.post()
					.uri("/trace_route")
					.contentType(MediaType.APPLICATION_JSON)
					.body(payload)
					.retrieve()
					.body(Map.class);
			List<ValhallaPath> paths = parseRouteBody(body);
			return paths.isEmpty() ? null : paths.getFirst();
		}
		catch (RuntimeException ex) {
			log.warn("Valhalla map match unavailable: {}", ex.getMessage());
			return null;
		}
	}

	private List<ValhallaPath> routeWithCosting(double originLat, double originLon, double destLat, double destLon,
			String costing) {
		try {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("locations", List.of(
					Map.of("lat", originLat, "lon", originLon),
					Map.of("lat", destLat, "lon", destLon)));
			payload.put("costing", costing);
			payload.put("alternates", this.properties.valhallaAlternates());
			payload.put("directions_options", Map.of("units", "kilometers"));
			@SuppressWarnings("unchecked")
			Map<String, Object> body = this.restClient.post()
					.uri("/route")
					.contentType(MediaType.APPLICATION_JSON)
					.body(payload)
					.retrieve()
					.body(Map.class);
			return parseRouteBody(body);
		}
		catch (RuntimeException ex) {
			log.warn("Valhalla route unavailable (costing={}): {}", costing, ex.getMessage());
			return List.of();
		}
	}

	static List<ValhallaPath> parseRouteBody(Map<String, Object> body) {
		List<ValhallaPath> paths = new ArrayList<>();
		if (body == null) {
			return paths;
		}
		addTrip(paths, body.get("trip"));
		if (body.get("alternates") instanceof List<?> alternates) {
			for (Object alternate : alternates) {
				if (alternate instanceof Map<?, ?> map) {
					addTrip(paths, map.get("trip"));
				}
			}
		}
		return List.copyOf(paths);
	}

	@SuppressWarnings("unchecked")
	private static void addTrip(List<ValhallaPath> paths, Object tripObj) {
		if (!(tripObj instanceof Map<?, ?> trip)) {
			return;
		}
		String shape = null;
		if (trip.get("legs") instanceof List<?> legs && !legs.isEmpty() && legs.getFirst() instanceof Map<?, ?> leg) {
			shape = leg.get("shape") instanceof String encoded ? encoded : null;
		}
		Map<String, Object> summary = trip.get("summary") instanceof Map<?, ?> map
				? (Map<String, Object>) map
				: Map.of();
		double distanceKm = asDouble(summary.get("length"));
		int durationMinutes = (int) Math.max(1, Math.round(asDouble(summary.get("time")) / 60d));
		if (shape == null || shape.isBlank()) {
			return;
		}
		String wkt = PolylineCodec.toLineStringWkt(PolylineCodec.decode(shape, 6));
		if (wkt.contains("EMPTY")) {
			return;
		}
		paths.add(new ValhallaPath(wkt, distanceKm, durationMinutes));
	}

	private static List<Map<String, Double>> shape(List<double[]> latLonPoints) {
		List<Map<String, Double>> shape = new ArrayList<>();
		for (double[] point : latLonPoints) {
			shape.add(Map.of("lat", point[0], "lon", point[1]));
		}
		return shape;
	}

	private static double asDouble(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		if (value == null) {
			return 0;
		}
		return Double.parseDouble(String.valueOf(value));
	}
}
