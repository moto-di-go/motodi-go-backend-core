package com.motodigo.core.pricing.dto;

import java.math.BigDecimal;

public record PricingBreakdown(
		BigDecimal baseFcfa,
		double distanceKm,
		BigDecimal pricePerKmFcfa,
		BigDecimal distanceAmountFcfa,
		BigDecimal nightMultiplier,
		BigDecimal weatherMultiplier,
		boolean nightWeatherApplicable,
		BigDecimal roundingFcfa,
		BigDecimal totalFcfa) {
}
