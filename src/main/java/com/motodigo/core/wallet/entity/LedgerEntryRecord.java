package com.motodigo.core.wallet.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryRecord(
		UUID id,
		UUID walletId,
		UUID paymentTransactionId,
		LedgerEntryType type,
		BigDecimal amount,
		String currency,
		String reference,
		Instant bookedAt,
		String idempotencyKey) {
}
