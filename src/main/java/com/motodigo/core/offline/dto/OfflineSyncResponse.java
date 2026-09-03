package com.motodigo.core.offline.dto;

import java.util.List;
import java.util.UUID;

public record OfflineSyncResponse(
		UUID batchId,
		String status,
		List<OfflineSyncItemResult> results) {
}
