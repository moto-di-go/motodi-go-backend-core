package com.motodigo.core.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record KpiSnapshotResponse(
		UUID id,
		LocalDate periodStart,
		LocalDate periodEnd,
		int monthlyActiveUsers,
		int activeVerifiedDrivers,
		int reservationCount,
		Map<String, Long> reservationsByStatus,
		BigDecimal walletVolumeFcfa,
		BigDecimal mtnMomoVolumeFcfa,
		BigDecimal orangeMoneyVolumeFcfa,
		BigDecimal slaAvailability,
		BigDecimal avgSigSyncSeconds,
		BigDecimal aiUsageRate,
		BmcTargetsResponse bmcTargetsM6,
		BmcTargetsResponse bmcTargetsM12,
		Instant createdAt) {
}
