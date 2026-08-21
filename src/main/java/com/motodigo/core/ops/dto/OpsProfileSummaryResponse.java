package com.motodigo.core.ops.dto;

import java.time.Instant;
import java.util.UUID;

public record OpsProfileSummaryResponse(
		UUID id,
		String type,
		String validationStatus,
		String displayName,
		boolean active,
		Instant createdAt) {
}
