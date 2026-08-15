package com.motodigo.core.geo.entity;

import java.util.List;
import java.util.UUID;

public record BuiltRoute(
		UUID id,
		double originLat,
		double originLon,
		double destinationLat,
		double destinationLon,
		String polylineWkt,
		double distanceKm,
		int durationMinutes,
		String status,
		List<BuiltRouteAlternative> alternatives,
		List<BuiltRouteOption> options) {

	public BuiltRoute {
		alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
		options = options == null ? List.of() : List.copyOf(options);
	}
}
