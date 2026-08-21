package com.motodigo.core.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.booking.dto.BookingResponse;
import com.motodigo.core.payment.entity.PaymentTransactionRecord;
import com.motodigo.core.payment.entity.ReceiptRecord;

public record RidePaymentResponse(
		UUID id,
		UUID bookingId,
		String status,
		BigDecimal amount,
		BigDecimal platformFee,
		BigDecimal transporterAmount,
		String currency,
		String provider,
		Instant initiatedAt,
		Instant confirmedAt,
		UUID receiptId,
		String receiptNumber,
		String receiptPdfUrl,
		BookingResponse booking) {

	public static RidePaymentResponse from(PaymentTransactionRecord tx, ReceiptRecord receipt,
			BookingResponse booking) {
		return new RidePaymentResponse(
				tx.id(),
				tx.bookingId(),
				tx.status().name(),
				tx.amount(),
				tx.platformFee(),
				tx.transporterAmount(),
				tx.currency(),
				tx.provider() == null ? null : tx.provider().name(),
				tx.initiatedAt(),
				tx.confirmedAt(),
				receipt == null ? null : receipt.id(),
				receipt == null ? null : receipt.receiptNumber(),
				receipt == null ? null : receipt.pdfUrl(),
				booking);
	}
}
