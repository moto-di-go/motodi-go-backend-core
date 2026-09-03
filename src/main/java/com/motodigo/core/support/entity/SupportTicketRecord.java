package com.motodigo.core.support.entity;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketRecord(
		UUID id,
		UUID userId,
		UUID assignedAdminProfileId,
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
}
