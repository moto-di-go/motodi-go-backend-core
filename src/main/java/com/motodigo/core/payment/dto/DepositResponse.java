package com.motodigo.core.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.payment.entity.PaymentTransactionRecord;

public record DepositResponse(
		UUID id,
		String status,
		BigDecimal amount,
		String currency,
		String provider,
		String payerPhone,
		String externalReference,
		String failureReason,
		Instant initiatedAt,
		Instant confirmedAt) {

	public static DepositResponse from(PaymentTransactionRecord tx) {
		return new DepositResponse(
				tx.id(),
				tx.status().name(),
				tx.amount(),
				tx.currency(),
				tx.provider() == null ? null : tx.provider().name(),
				tx.payerPhone(),
				tx.externalReference(),
				tx.failureReason(),
				tx.initiatedAt(),
				tx.confirmedAt());
	}
}
