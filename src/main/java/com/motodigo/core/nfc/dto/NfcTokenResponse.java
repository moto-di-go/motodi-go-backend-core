package com.motodigo.core.nfc.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.nfc.entity.NfcPaymentTokenRecord;

public record NfcTokenResponse(
		UUID id,
		UUID jti,
		String signedToken,
		BigDecimal amount,
		String currency,
		String status,
		UUID bookingId,
		UUID tripId,
		Instant issuedAt,
		Instant expiresAt,
		Instant consumedAt) {

	public static NfcTokenResponse from(NfcPaymentTokenRecord token) {
		return new NfcTokenResponse(
				token.id(),
				token.jti(),
				token.signedToken(),
				token.amount(),
				"XAF",
				token.status().name(),
				token.bookingId(),
				token.tripId(),
				token.issuedAt(),
				token.expiresAt(),
				token.consumedAt());
	}
}
