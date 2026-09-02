package com.motodigo.core.agency.dto;

import java.util.List;

public record AgencyAnnouncementPageResponse(
		List<AgencyAnnouncementSummaryResponse> announcements,
		int page,
		int size,
		boolean hasMore) {
}
