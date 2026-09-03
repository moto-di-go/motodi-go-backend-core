package com.motodigo.core.offline.entity;

import java.time.Instant;
import java.util.UUID;

public record SyncBatchRecord(
		UUID id,
		UUID userId,
		Instant startedAt,
		Instant completedAt,
		String status) {
}
