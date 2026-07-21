package com.motodigo.core.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank String phoneNumber) {
}
