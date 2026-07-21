package com.motodigo.core.auth.dto;

import com.motodigo.core.auth.entity.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(
		@NotBlank String phoneNumber,
		@NotBlank @Size(min = 4, max = 10) String code,
		OtpPurpose purpose) {
}
