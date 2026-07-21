package com.motodigo.core.auth.entity;

import java.time.Instant;
import java.util.UUID;

public record UserRecord(
		UUID id,
		String phoneNumber,
		String passwordHash,
		String fullName,
		String email,
		String locale,
		boolean cguAccepted,
		UserStatus status,
		Instant phoneVerifiedAt,
		Instant createdAt,
		Instant updatedAt) {
}
