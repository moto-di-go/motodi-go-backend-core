package com.motodigo.core.auth.dto;

import java.util.UUID;

public record OtpVerifyResponse(UUID userId, String phoneNumber, String status) {
}
