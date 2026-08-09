package com.motodigo.core.intercity.dto;

import java.util.List;

public record IntercityAnnouncementMinePageResponse(
		List<IntercityAnnouncementResponse> items,
		int page,
		int size,
		boolean hasMore) {
}
