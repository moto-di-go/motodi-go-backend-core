package com.motodigo.core.auth.entity;

import java.time.Instant;
import java.util.UUID;

public record OtpRecord(
		UUID id,
		UUID userId,
		String destinationPhone,
		OtpPurpose purpose,
		String codeHash,
		int attempts,
		Instant expiresAt,
		Instant consumedAt,
		Instant createdAt) {
}
