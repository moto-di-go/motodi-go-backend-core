package com.motodigo.core.booking.dto;

import java.util.List;

public record BookingPageResponse(
		List<BookingResponse> items,
		int page,
		int size,
		boolean hasMore) {
}
