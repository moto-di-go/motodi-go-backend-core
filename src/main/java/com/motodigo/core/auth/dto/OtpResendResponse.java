package com.motodigo.core.auth.dto;

import java.time.Instant;

public record OtpResendResponse(String phoneNumber, String purpose, Instant otpExpiresAt) {
}
