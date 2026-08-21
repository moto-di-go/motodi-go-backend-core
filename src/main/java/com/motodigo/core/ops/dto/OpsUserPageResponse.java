package com.motodigo.core.ops.dto;

import java.util.List;

public record OpsUserPageResponse(
		List<OpsUserSummaryResponse> users,
		int page,
		int size,
		boolean hasMore) {
}
