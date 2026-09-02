package com.motodigo.core.offline.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.motodigo.core.offline.entity.OfflineOperationRecord;

public record OfflineOperationResponse(
		UUID id,
		String type,
		String status,
		String idempotencyKey,
		Map<String, Object> payload,
		Instant queuedAt,
		Instant syncedAt,
		String conflictCode,
		String conflictMessage) {

	public static OfflineOperationResponse from(OfflineOperationRecord record, Map<String, Object> payload) {
		return new OfflineOperationResponse(
				record.id(),
				record.type(),
				record.status(),
				record.idempotencyKey(),
				payload,
				record.queuedAt(),
				record.syncedAt(),
				record.conflictCode(),
				record.conflictMessage());
	}
}
