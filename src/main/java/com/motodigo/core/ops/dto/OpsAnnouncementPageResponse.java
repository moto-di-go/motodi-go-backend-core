package com.motodigo.core.ops.dto;

import java.util.List;

public record OpsAnnouncementPageResponse(
		List<OpsAnnouncementSummaryResponse> announcements,
		int page,
		int size,
		boolean hasMore) {
}
