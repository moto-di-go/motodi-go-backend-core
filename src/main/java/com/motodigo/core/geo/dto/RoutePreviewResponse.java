package com.motodigo.core.geo.dto;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.geo.entity.BuiltRoute;
import com.motodigo.core.geo.entity.BuiltRouteAlternative;
import com.motodigo.core.geo.entity.BuiltRouteOption;

public record RoutePreviewResponse(
		UUID routeId,
		String engine,
		String polyline,
		double distanceKm,
		int durationMinutes,
		String status,
		List<BuiltRouteOption> options,
		List<BuiltRouteAlternative> alternatives) {

	public static RoutePreviewResponse from(BuiltRoute route, String engine) {
		return new RoutePreviewResponse(route.id(), engine, route.polylineWkt(), route.distanceKm(),
				route.durationMinutes(), route.status(), route.options(), route.alternatives());
	}
}
