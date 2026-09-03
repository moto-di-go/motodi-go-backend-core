package com.motodigo.core.support.entity;

import java.time.Instant;
import java.util.UUID;

public record ModerationActionRecord(
		UUID id,
		UUID adminProfileId,
		String targetType,
		UUID targetId,
		String action,
		String reason,
		Instant actedAt) {
}
