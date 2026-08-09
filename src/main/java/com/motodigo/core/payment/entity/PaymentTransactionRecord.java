package com.motodigo.core.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionRecord(
		UUID id,
		PaymentType type,
		PaymentStatus status,
		BigDecimal amount,
		String currency,
		String externalReference,
		String idempotencyKey,
		PaymentProvider provider,
		String payerPhone,
		String callbackSignature,
		UUID passengerWalletId,
		UUID receiverWalletId,
		Instant initiatedAt,
		Instant confirmedAt,
		String failureReason,
		String metadataJson) {
}
