package com.motodigo.core.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceiptRecord(
		UUID id,
		UUID paymentTransactionId,
		String receiptNumber,
		BigDecimal amount,
		String currency,
		Instant issuedAt,
		String pdfUrl) {
}
