package com.motodigo.core.pricing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.geo.entity.BuiltRouteAlternative;

public record PricingEstimateResponse(
		UUID id,
		UUID routeId,
		BigDecimal amountFcfa,
		double distanceKm,
		int durationMinutes,
		String appliedRulesTrace,
		String polyline,
		String status,
		List<BuiltRouteAlternative> alternatives,
		Instant calculatedAt,
		Instant expiresAt,
		PricingBreakdown breakdown,
		List<PricingRouteOption> options) {

	public PricingEstimateResponse {
		alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
		options = options == null ? List.of() : List.copyOf(options);
	}
}
