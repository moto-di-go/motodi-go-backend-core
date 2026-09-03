package com.motodigo.core.support.dto;

import java.time.Instant;
import java.util.UUID;

public record ModerationQueueItem(
		String targetType,
		UUID targetId,
		String priority,
		String reason,
		String preview,
		Instant createdAt) {
}
