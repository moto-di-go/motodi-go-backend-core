package com.motodigo.core.pricing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PricingEstimateRequest(
		@NotNull @DecimalMin("-90") @DecimalMax("90") Double originLat,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double originLon,
		@NotNull @DecimalMin("-90") @DecimalMax("90") Double destinationLat,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double destinationLon,
		String serviceType) {
}
