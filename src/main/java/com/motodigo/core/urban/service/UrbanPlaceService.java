package com.motodigo.core.urban.service;

import java.util.Comparator;
import java.util.List;

import com.motodigo.core.geo.config.GeoProperties;
import com.motodigo.core.geo.exception.GeoErrorCode;
import com.motodigo.core.geo.exception.GeoException;
import com.motodigo.core.intercity.support.AccentFold;
import com.motodigo.core.urban.dto.UrbanPlaceResponse;
import com.motodigo.core.urban.entity.UrbanPlaceRecord;
import com.motodigo.core.urban.repository.UrbanPlaceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class UrbanPlaceService {

	private static final int GEOCODE_LIMIT = 15;

	private final UrbanPlaceRepository urbanPlaceRepository;
	private final GeoProperties geoProperties;

	public UrbanPlaceService(UrbanPlaceRepository urbanPlaceRepository, GeoProperties geoProperties) {
		this.urbanPlaceRepository = urbanPlaceRepository;
		this.geoProperties = geoProperties;
	}

	@Transactional(readOnly = true)
	public List<UrbanPlaceResponse> popular() {
		return this.urbanPlaceRepository.findPopular().stream()
				.map(UrbanPlaceService::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<UrbanPlaceResponse> geocode(String query) {
		return geocode(query, null);
	}

	@Transactional(readOnly = true)
	public List<UrbanPlaceResponse> geocode(String query, String kind) {
		String folded = AccentFold.fold(query);
		if (folded == null) {
			return List.of();
		}
		List<UrbanPlaceRecord> places = kind == null || kind.isBlank()
				? this.urbanPlaceRepository.findAllInServiceArea()
				: this.urbanPlaceRepository.findByKind(kind.trim().toUpperCase());
		return search(folded, places);
	}

	@Transactional(readOnly = true)
	public UrbanPlaceResponse reverse(double lat, double lon) {
		if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
			throw new GeoException(GeoErrorCode.POSITION_INVALID);
		}
		if (!this.geoProperties.isInsideYaounde(lat, lon)) {
			throw new GeoException(GeoErrorCode.OUTSIDE_SERVICE_AREA);
		}
		return this.urbanPlaceRepository.findAllInServiceArea().stream()
				.min(Comparator.comparingDouble(place -> UrbanSearchService.haversineKm(lat, lon, place.lat(), place.lon())))
				.map(UrbanPlaceService::toResponse)
				.orElseThrow(() -> new GeoException(GeoErrorCode.ROUTE_NOT_FOUND));
	}

	static List<UrbanPlaceResponse> search(String foldedQuery, List<UrbanPlaceRecord> places) {
		return places.stream()
				.filter(place -> matches(place, foldedQuery))
				.sorted(Comparator
						.comparingInt((UrbanPlaceRecord place) -> prefixRank(place, foldedQuery))
						.thenComparing(UrbanPlaceRecord::sortOrder)
						.thenComparing(UrbanPlaceRecord::label, String.CASE_INSENSITIVE_ORDER))
				.limit(GEOCODE_LIMIT)
				.map(UrbanPlaceService::toResponse)
				.toList();
	}

	static boolean matches(UrbanPlaceRecord place, String foldedQuery) {
		String foldedLabel = AccentFold.fold(place.label());
		if (foldedLabel == null) {
			return false;
		}
		if (foldedLabel.contains(foldedQuery) || compact(foldedLabel).contains(compact(foldedQuery))) {
			return true;
		}
		for (String word : foldedLabel.split(" ")) {
			if (word.startsWith(foldedQuery) || compact(word).startsWith(compact(foldedQuery))) {
				return true;
			}
			if (foldedQuery.length() >= 4 && levenshtein(word, foldedQuery) <= 1) {
				return true;
			}
		}
		return foldedQuery.length() >= 4 && levenshtein(foldedLabel, foldedQuery) <= 2;
	}

	static String compact(String value) {
		return value.replace("-", "").replace(" ", "").replace("'", "");
	}

	private static int prefixRank(UrbanPlaceRecord place, String foldedQuery) {
		String foldedLabel = AccentFold.fold(place.label());
		if (foldedLabel != null && (foldedLabel.startsWith(foldedQuery)
				|| compact(foldedLabel).startsWith(compact(foldedQuery)))) {
			return 0;
		}
		return 1;
	}

	private static UrbanPlaceResponse toResponse(UrbanPlaceRecord place) {
		return UrbanPlaceResponse.local(place.id(), place.label(), place.lat(), place.lon(), place.kind());
	}

	static int levenshtein(String left, String right) {
		int[] previous = new int[right.length() + 1];
		int[] current = new int[right.length() + 1];
		for (int j = 0; j <= right.length(); j++) {
			previous[j] = j;
		}
		for (int i = 1; i <= left.length(); i++) {
			current[0] = i;
			for (int j = 1; j <= right.length(); j++) {
				int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[right.length()];
	}
}
