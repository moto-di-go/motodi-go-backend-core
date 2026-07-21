package com.motodigo.core.auth.entity;

import java.time.Instant;
import java.util.UUID;

public record SessionRecord(
		UUID id,
		UUID userId,
		String accessTokenHash,
		String refreshTokenHash,
		String deviceId,
		String ipAddress,
		Instant issuedAt,
		Instant expiresAt,
		Instant revokedAt,
		SessionStatus status) {
}
