package com.motodigo.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank String phoneNumber,
		@NotBlank @Size(min = 1, max = 128) String password,
		@Size(max = 160) String deviceId) {
}
