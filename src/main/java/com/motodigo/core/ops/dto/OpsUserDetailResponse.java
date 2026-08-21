package com.motodigo.core.ops.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OpsUserDetailResponse(
		UUID id,
		String phoneNumber,
		String fullName,
		String email,
		String locale,
		String status,
		Instant phoneVerifiedAt,
		Instant createdAt,
		Instant updatedAt,
		List<OpsProfileSummaryResponse> profiles) {
}
