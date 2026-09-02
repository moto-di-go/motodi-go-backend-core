package com.motodigo.core.support.dto;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.support.entity.ModerationActionRecord;

public record ModerationActionResponse(
		UUID id,
		String targetType,
		UUID targetId,
		String action,
		String reason,
		Instant actedAt) {

	public static ModerationActionResponse from(ModerationActionRecord action) {
		return new ModerationActionResponse(
				action.id(),
				action.targetType(),
				action.targetId(),
				action.action(),
				action.reason(),
				action.actedAt());
	}
}
