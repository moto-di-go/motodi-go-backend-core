package com.motodigo.core.agency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record AgencyOverviewResponse(
		long activeAnnouncements,
		long reservationCount,
		BigDecimal averageFillRate,
		String billingPlan,
		int billingPlanPriceFcfa,
		int displayPriority,
		String legalName,
		Map<String, Long> reservationsByStatus,
		LocalDate periodStart,
		LocalDate periodEnd) {
}
