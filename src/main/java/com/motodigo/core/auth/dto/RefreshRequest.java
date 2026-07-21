package com.motodigo.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(@NotBlank String refreshToken, @Size(max = 160) String deviceId) {
}
