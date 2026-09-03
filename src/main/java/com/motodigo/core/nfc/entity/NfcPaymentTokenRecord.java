package com.motodigo.core.nfc.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NfcPaymentTokenRecord(
		UUID id,
		UUID walletId,
		UUID paymentTransactionId,
		String signedToken,
		BigDecimal amount,
		Instant issuedAt,
		Instant expiresAt,
		Instant consumedAt,
		NfcTokenStatus status,
		UUID jti,
		UUID bookingId,
		UUID tripId,
		UUID payerUserId,
		UUID receiverWalletId,
		String payloadJson) {
}
