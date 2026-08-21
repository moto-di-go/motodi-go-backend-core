package com.motodigo.core.ops.dto;

import java.time.Instant;
import java.util.UUID;

public record OpsProfileListItemResponse(
		UUID id,
		UUID userId,
		String type,
		String validationStatus,
		String displayName,
		boolean active,
		Instant createdAt,
		String userPhone,
		String userStatus) {
}
