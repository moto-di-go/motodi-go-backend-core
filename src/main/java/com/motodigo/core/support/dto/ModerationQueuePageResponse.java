package com.motodigo.core.support.dto;

import java.util.List;

public record ModerationQueuePageResponse(
		List<ModerationQueueItem> items,
		int page,
		int size,
		boolean hasMore) {
}
