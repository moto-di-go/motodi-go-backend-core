package com.motodigo.core.nfc.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record NfcTokenIssueRequest(
		@NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal amount,
		@NotNull String currency,
		UUID bookingId,
		UUID tripId,
		UUID receiverWalletId) {
}
