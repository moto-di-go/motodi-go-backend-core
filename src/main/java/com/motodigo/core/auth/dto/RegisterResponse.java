package com.motodigo.core.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record RegisterResponse(UUID userId, String phoneNumber, String status, Instant otpExpiresAt) {
}
