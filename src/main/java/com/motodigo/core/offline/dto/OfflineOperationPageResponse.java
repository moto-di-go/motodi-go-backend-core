package com.motodigo.core.offline.dto;

import java.util.List;

public record OfflineOperationPageResponse(
		List<OfflineOperationResponse> operations,
		int page,
		int size,
		boolean hasMore) {
}
