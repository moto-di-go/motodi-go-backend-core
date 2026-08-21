package com.motodigo.core.ops.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OpsPaymentSummaryResponse(
		UUID id,
		String type,
		String status,
		BigDecimal amount,
		String currency,
		String provider,
		String payerPhone,
		String externalReference,
		Instant initiatedAt,
		Instant confirmedAt) {
}
