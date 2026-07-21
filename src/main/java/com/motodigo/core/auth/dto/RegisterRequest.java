package com.motodigo.core.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank String phoneNumber,
		@NotBlank @Size(min = 8, max = 128) String password,
		@Size(max = 160) String fullName,
		@Size(max = 255) String email,
		@Size(max = 16) String locale,
		@AssertTrue boolean cguAccepted,
		@Size(max = 160) String deviceId) {
}
