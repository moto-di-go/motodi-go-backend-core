package com.motodigo.core.payment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DepositRequest(
		@NotNull @DecimalMin(value = "1.00", inclusive = true) BigDecimal amount,
		@NotBlank @Pattern(regexp = "XAF") String currency,
		@NotBlank String provider,
		@NotBlank @Pattern(regexp = "^\\+237[6-9]\\d{8}$") String phoneNumber) {
}
