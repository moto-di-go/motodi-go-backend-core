package com.motodigo.core.ops.dto;

import java.util.Map;

public record OpsOverviewResponse(
		long usersTotal,
		Map<String, Long> usersByStatus,
		long announcementsActive,
		Map<String, Long> announcementsByStatus,
		Map<String, Long> bookingsByStatus,
		long paymentsPendingToday,
		long paymentsConfirmedToday,
		String paymentsNote) {
}
