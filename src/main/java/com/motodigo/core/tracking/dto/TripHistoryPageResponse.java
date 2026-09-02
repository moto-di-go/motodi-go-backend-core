package com.motodigo.core.tracking.dto;

import java.util.List;

public record TripHistoryPageResponse(
		List<TripHistoryItem> items,
		int page,
		int size,
		boolean hasMore) {
}
