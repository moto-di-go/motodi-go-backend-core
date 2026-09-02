package com.motodigo.core.wallet.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletRecord(
		UUID id,
		UUID userId,
		String currency,
		BigDecimal currentBalance,
		BigDecimal heldBalance,
		WalletStatus status,
		Instant createdAt,
		Instant updatedAt) {

	public BigDecimal availableBalance() {
		BigDecimal held = heldBalance == null ? BigDecimal.ZERO : heldBalance;
		return currentBalance.subtract(held);
	}
}
