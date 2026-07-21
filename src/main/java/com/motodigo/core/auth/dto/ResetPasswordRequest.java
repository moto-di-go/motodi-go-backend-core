package com.motodigo.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@NotBlank String phoneNumber,
		@NotBlank @Size(min = 4, max = 10) String code,
		@NotBlank @Size(min = 8, max = 128) String newPassword) {
}
