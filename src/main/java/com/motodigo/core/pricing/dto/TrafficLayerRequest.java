package com.motodigo.core.pricing.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;

public record TrafficLayerRequest(
		@NotBlank String type,
		@NotBlank String label,
		@NotBlank String geometryWkt,
		String severity,
		Instant validFrom,
		Instant validTo,
		Boolean active,
		String source) {
}
