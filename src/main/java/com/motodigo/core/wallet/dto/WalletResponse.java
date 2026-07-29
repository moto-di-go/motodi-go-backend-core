package com.motodigo.core.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.wallet.entity.WalletRecord;

public record WalletResponse(
		UUID id,
		String currency,
		BigDecimal currentBalance,
		String status,
		Instant createdAt) {

	public static WalletResponse from(WalletRecord wallet) {
		return new WalletResponse(
				wallet.id(),
				wallet.currency(),
				wallet.currentBalance(),
				wallet.status().name(),
				wallet.createdAt());
	}
}
