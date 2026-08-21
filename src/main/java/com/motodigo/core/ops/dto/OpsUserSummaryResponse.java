package com.motodigo.core.ops.dto;

import java.time.Instant;
import java.util.UUID;

public record OpsUserSummaryResponse(
		UUID id,
		String phoneNumber,
		String fullName,
		String email,
		String status,
		Instant createdAt,
		int profilesCount) {
}
