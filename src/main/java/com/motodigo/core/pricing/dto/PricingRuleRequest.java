package com.motodigo.core.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PricingRuleRequest(
		@NotBlank String zoneCode,
		String zoneWkt,
		@NotNull LocalTime timeStart,
		@NotNull LocalTime timeEnd,
		@NotNull @DecimalMin("0") BigDecimal baseFare,
		@NotNull @DecimalMin("0") BigDecimal pricePerKm,
		@DecimalMin("0.001") BigDecimal nightCoefficient,
		@DecimalMin("0.001") BigDecimal weatherCoefficient,
		Boolean active) {
}
