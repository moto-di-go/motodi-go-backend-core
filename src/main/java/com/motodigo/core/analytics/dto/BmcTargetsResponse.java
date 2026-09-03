package com.motodigo.core.analytics.dto;

import java.math.BigDecimal;

public record BmcTargetsResponse(
		int monthlyActiveUsers,
		int activeVerifiedDrivers,
		BigDecimal slaAvailabilityPercent) {
}
