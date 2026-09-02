package com.motodigo.core.support.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportTicketCreateRequest(
		@NotBlank String category,
		@NotBlank String priority,
		@NotBlank @Size(max = 180) String subject,
		@NotBlank String message,
		UUID tripId,
		UUID bookingId,
		UUID paymentTransactionId,
		UUID offlineOperationId) {
}
