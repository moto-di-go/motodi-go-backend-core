package com.motodigo.core.urban.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.motodigo.core.geo.client.PhotonClient;
import com.motodigo.core.geo.exception.GeoErrorCode;
import com.motodigo.core.geo.exception.GeoException;
import com.motodigo.core.intercity.support.AccentFold;
import com.motodigo.core.urban.dto.UrbanPlaceResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PlaceSearchFacade {

	private static final int LIMIT = 15;
	private static final double DUPLICATE_KM = 0.12;

	private final UrbanPlaceService urbanPlaceService;
	private final PhotonClient photonClient;

	public PlaceSearchFacade(UrbanPlaceService urbanPlaceService, PhotonClient photonClient) {
		this.urbanPlaceService = urbanPlaceService;
		this.photonClient = photonClient;
	}

	public List<UrbanPlaceResponse> suggest(String query, Double lat, Double lon) {
		return suggest(query, lat, lon, null);
	}

	public List<UrbanPlaceResponse> suggest(String query, Double lat, Double lon, String kind) {
		List<UrbanPlaceResponse> local = this.urbanPlaceService.geocode(query, kind);
		boolean city = kind != null && "CITY".equalsIgnoreCase(kind.trim());
		Double biasLat = city ? null : lat;
		Double biasLon = city ? null : lon;
		List<UrbanPlaceResponse> remote = this.photonClient.search(query, biasLat, biasLon, LIMIT, kind);
		return merge(local, remote, biasLat, biasLon);
	}

	public UrbanPlaceResponse reverse(double lat, double lon) {
		UrbanPlaceResponse local;
		try {
			local = this.urbanPlaceService.reverse(lat, lon);
		}
		catch (GeoException ex) {
			if (ex.errorCode() == GeoErrorCode.OUTSIDE_SERVICE_AREA
					|| ex.errorCode() == GeoErrorCode.POSITION_INVALID) {
				throw ex;
			}
			local = null;
		}
		UrbanPlaceResponse remote = this.photonClient.reverse(lat, lon);
		if (remote == null) {
			if (local == null) {
				throw new GeoException(GeoErrorCode.ROUTE_NOT_FOUND);
			}
			return withDistance(local, lat, lon);
		}
		if (local == null) {
			return withDistance(remote, lat, lon);
		}
		double localKm = UrbanSearchService.haversineKm(lat, lon, local.lat(), local.lon());
		double remoteKm = UrbanSearchService.haversineKm(lat, lon, remote.lat(), remote.lon());
		if (localKm <= DUPLICATE_KM || localKm <= remoteKm) {
			return local.withDistanceKm(roundKm(localKm));
		}
		return remote.withDistanceKm(roundKm(remoteKm));
	}

	static List<UrbanPlaceResponse> merge(List<UrbanPlaceResponse> local, List<UrbanPlaceResponse> remote, Double lat,
			Double lon) {
		Map<String, UrbanPlaceResponse> merged = new LinkedHashMap<>();
		for (UrbanPlaceResponse place : local) {
			UrbanPlaceResponse ranked = withDistance(place, lat, lon);
			merged.put(dedupeKey(ranked), ranked);
		}
		for (UrbanPlaceResponse place : remote) {
			UrbanPlaceResponse ranked = withDistance(place, lat, lon);
			if (isDuplicate(merged.values(), ranked)) {
				continue;
			}
			merged.put(dedupeKey(ranked), ranked);
			if (merged.size() >= LIMIT) {
				break;
			}
		}
		List<UrbanPlaceResponse> result = new ArrayList<>(merged.values());
		result.sort((left, right) -> {
			int source = sourceRank(left.source()) - sourceRank(right.source());
			if (source != 0) {
				return source;
			}
			Double leftKm = left.distanceKm();
			Double rightKm = right.distanceKm();
			if (leftKm != null && rightKm != null) {
				return Double.compare(leftKm, rightKm);
			}
			return left.label().compareToIgnoreCase(right.label());
		});
		if (result.size() > LIMIT) {
			return List.copyOf(result.subList(0, LIMIT));
		}
		return List.copyOf(result);
	}

	private static boolean isDuplicate(Iterable<UrbanPlaceResponse> existing, UrbanPlaceResponse candidate) {
		String folded = AccentFold.fold(candidate.label());
		for (UrbanPlaceResponse place : existing) {
			if (folded != null && folded.equals(AccentFold.fold(place.label()))) {
				return true;
			}
			double km = UrbanSearchService.haversineKm(place.lat(), place.lon(), candidate.lat(), candidate.lon());
			if (km <= DUPLICATE_KM) {
				return true;
			}
		}
		return false;
	}

	private static UrbanPlaceResponse withDistance(UrbanPlaceResponse place, Double lat, Double lon) {
		if (place == null) {
			return null;
		}
		if (lat == null || lon == null) {
			return place;
		}
		return place.withDistanceKm(roundKm(UrbanSearchService.haversineKm(lat, lon, place.lat(), place.lon())));
	}

	private static String dedupeKey(UrbanPlaceResponse place) {
		String folded = AccentFold.fold(place.label());
		return (folded == null ? place.label() : folded) + "@" + Math.round(place.lat() * 10000) + ","
				+ Math.round(place.lon() * 10000);
	}

	private static int sourceRank(String source) {
		return "LOCAL".equalsIgnoreCase(source) ? 0 : 1;
	}

	private static double roundKm(double km) {
		return Math.round(km * 1000d) / 1000d;
	}
}
