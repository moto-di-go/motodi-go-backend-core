package com.motodigo.core.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.wallet.entity.WalletRecord;

public record WalletResponse(
		UUID id,
		String currency,
		BigDecimal currentBalance,
		BigDecimal heldBalance,
		BigDecimal availableBalance,
		String status,
		Instant createdAt,
		BigDecimal nfcOfflineQuotaRemaining,
		Instant nfcOfflineQuotaExpiresAt) {

	public static WalletResponse from(WalletRecord wallet) {
		return from(wallet, null, null);
	}

	public static WalletResponse from(WalletRecord wallet, BigDecimal nfcOfflineQuotaRemaining,
			Instant nfcOfflineQuotaExpiresAt) {
		BigDecimal held = wallet.heldBalance() == null ? BigDecimal.ZERO : wallet.heldBalance();
		return new WalletResponse(
				wallet.id(),
				wallet.currency(),
				wallet.currentBalance(),
				held,
				wallet.availableBalance(),
				wallet.status().name(),
				wallet.createdAt(),
				nfcOfflineQuotaRemaining,
				nfcOfflineQuotaExpiresAt);
	}
}
