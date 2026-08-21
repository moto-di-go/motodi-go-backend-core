package com.motodigo.core.ops.dto;

import java.util.List;

public record OpsProfilePageResponse(
		List<OpsProfileListItemResponse> profiles,
		int page,
		int size,
		boolean hasMore) {
}
