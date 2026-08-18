package com.motodigo.core.wallet.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletRecord(
		UUID id,
		UUID userId,
		String currency,
		BigDecimal currentBalance,
		WalletStatus status,
		Instant createdAt,
		Instant updatedAt) {
}
