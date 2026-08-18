package com.motodigo.core.geo.entity;

public record BuiltRouteAlternative(
		String reason,
		String polylineWkt,
		double extraDistanceKm,
		int extraDurationMinutes) {
}
