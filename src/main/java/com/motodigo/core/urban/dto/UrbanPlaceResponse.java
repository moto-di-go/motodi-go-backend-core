package com.motodigo.core.urban.dto;

import java.util.UUID;

public record UrbanPlaceResponse(
		UUID id,
		String label,
		double lat,
		double lon,
		String kind,
		String subtitle,
		String source,
		Double distanceKm) {

	public UrbanPlaceResponse {
		if (source == null || source.isBlank()) {
			source = "LOCAL";
		}
	}

	public static UrbanPlaceResponse local(UUID id, String label, double lat, double lon, String kind) {
		return new UrbanPlaceResponse(id, label, lat, lon, kind, null, "LOCAL", null);
	}

	public UrbanPlaceResponse withDistanceKm(Double km) {
		return new UrbanPlaceResponse(this.id, this.label, this.lat, this.lon, this.kind, this.subtitle, this.source,
				km);
	}
}
