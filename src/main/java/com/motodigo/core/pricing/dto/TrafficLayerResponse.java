package com.motodigo.core.pricing.dto;

import java.time.Instant;
import java.util.UUID;

public record TrafficLayerResponse(
		UUID id,
		String type,
		String label,
		String geometryWkt,
		String geoJson,
		String severity,
		Instant validFrom,
		Instant validTo,
		boolean active,
		String source,
		Instant updatedAt,
		boolean fresh) {
}
