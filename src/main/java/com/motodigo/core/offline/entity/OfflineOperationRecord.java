package com.motodigo.core.offline.entity;

import java.time.Instant;
import java.util.UUID;

public record OfflineOperationRecord(
		UUID id,
		UUID userId,
		UUID syncBatchId,
		String type,
		String payloadJson,
		String idempotencyKey,
		String status,
		int retryCount,
		Instant queuedAt,
		Instant syncedAt,
		UUID bookingId,
		UUID nfcTokenId,
		String conflictCode,
		String conflictMessage) {
}
