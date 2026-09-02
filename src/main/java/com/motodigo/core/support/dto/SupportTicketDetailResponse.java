package com.motodigo.core.support.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.support.entity.SupportTicketMessageRecord;
import com.motodigo.core.support.entity.SupportTicketRecord;

public record SupportTicketDetailResponse(
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
		Instant closedAt,
		List<SupportTicketMessageResponse> messages) {

	public static SupportTicketDetailResponse from(SupportTicketRecord ticket,
			List<SupportTicketMessageRecord> messages) {
		return new SupportTicketDetailResponse(
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
				ticket.closedAt(),
				messages.stream().map(SupportTicketMessageResponse::from).toList());
	}
}
