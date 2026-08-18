package com.motodigo.core.geo.entity;

public record BuiltRouteOption(
		String kind,
		String label,
		boolean degraded,
		double distanceKm,
		int durationMinutes,
		String polylineWkt,
		String reason) {

	public static final String RECOMMENDED = "RECOMMENDED";
	public static final String FASTEST = "FASTEST";
	public static final String CHEAPEST = "CHEAPEST";
}
