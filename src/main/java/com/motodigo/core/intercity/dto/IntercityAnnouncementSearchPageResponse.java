package com.motodigo.core.intercity.dto;

import java.util.List;

public record IntercityAnnouncementSearchPageResponse(
		List<IntercityAnnouncementResponse> items,
		int page,
		int size,
		boolean hasMore,
		List<WideningSuggestion> wideningSuggestions) {
}
