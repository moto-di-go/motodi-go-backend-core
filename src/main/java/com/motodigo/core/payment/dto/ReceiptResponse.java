package com.motodigo.core.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.payment.entity.ReceiptRecord;

public record ReceiptResponse(
		UUID id,
		UUID paymentTransactionId,
		String receiptNumber,
		BigDecimal amount,
		String currency,
		String pdfUrl,
		Instant issuedAt) {

	public static ReceiptResponse from(ReceiptRecord receipt) {
		return new ReceiptResponse(
				receipt.id(),
				receipt.paymentTransactionId(),
				receipt.receiptNumber(),
				receipt.amount(),
				receipt.currency(),
				receipt.pdfUrl(),
				receipt.issuedAt());
	}
}
