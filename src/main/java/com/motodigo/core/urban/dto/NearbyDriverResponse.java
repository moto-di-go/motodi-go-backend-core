package com.motodigo.core.urban.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NearbyDriverResponse(
		UUID driverProfileId,
		double lat,
		double lon,
		double approachDistanceKm,
		String availability,
		BigDecimal estimatedAmountFcfa,
		double tripDistanceKm,
		int tripDurationMinutes,
		String appliedRulesTrace,
		Instant lastSeenAt) {
}
