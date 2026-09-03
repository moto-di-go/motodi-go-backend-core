package com.motodigo.core.agency.dto;

import java.util.List;

public record AgencyBookingPageResponse(
		List<AgencyBookingSummaryResponse> bookings,
		int page,
		int size,
		boolean hasMore) {
}
