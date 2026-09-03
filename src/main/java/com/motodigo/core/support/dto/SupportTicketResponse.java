package com.motodigo.core.support.dto;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.support.entity.SupportTicketRecord;

public record SupportTicketResponse(
		UUID id,
		String ticketNumber,
		String category,
		String priority,
		String status,
		String subject,
		String message,
		UUID tripId,
		UUID bookingId,
		UUID paymentTransactionId,
		UUID offlineOperationId,
		Instant createdAt,
		Instant closedAt) {

	public static SupportTicketResponse from(SupportTicketRecord ticket) {
		return new SupportTicketResponse(
				ticket.id(),
				ticket.ticketNumber(),
				ticket.category(),
				ticket.priority(),
				ticket.status(),
				ticket.subject(),
				ticket.message(),
				ticket.tripId(),
				ticket.bookingId(),
				ticket.paymentTransactionId(),
				ticket.offlineOperationId(),
				ticket.createdAt(),
				ticket.closedAt());
	}
}
