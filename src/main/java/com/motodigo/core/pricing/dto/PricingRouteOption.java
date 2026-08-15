package com.motodigo.core.pricing.dto;

import java.math.BigDecimal;

public record PricingRouteOption(
		String kind,
		String label,
		boolean degraded,
		double distanceKm,
		int durationMinutes,
		BigDecimal amountFcfa,
		String polyline,
		String reason) {
}
