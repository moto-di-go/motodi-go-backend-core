package com.motodigo.core.offline.dto;

import java.util.UUID;

public record OfflineSyncItemResult(
		String idempotencyKey,
		String outcome,
		UUID operationId,
		String conflictCode,
		String message) {
}
