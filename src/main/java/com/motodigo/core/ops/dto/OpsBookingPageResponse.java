package com.motodigo.core.ops.dto;

import java.util.List;

public record OpsBookingPageResponse(
		List<OpsBookingSummaryResponse> bookings,
		int page,
		int size,
		boolean hasMore) {
}
