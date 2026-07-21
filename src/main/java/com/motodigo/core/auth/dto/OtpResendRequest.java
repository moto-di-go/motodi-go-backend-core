package com.motodigo.core.auth.dto;

import com.motodigo.core.auth.entity.OtpPurpose;
import jakarta.validation.constraints.NotBlank;

public record OtpResendRequest(@NotBlank String phoneNumber, OtpPurpose purpose) {
}
