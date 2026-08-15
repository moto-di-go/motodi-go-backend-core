package com.motodigo.core.pricing.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record PricingRuleRecord(
		UUID id,
		String zoneCode,
		String zoneGeoJson,
		LocalTime timeStart,
		LocalTime timeEnd,
		BigDecimal baseFare,
		BigDecimal pricePerKm,
		BigDecimal nightCoefficient,
		BigDecimal weatherCoefficient,
		boolean active,
		Instant effectiveFrom,
		Instant effectiveTo) {
}
