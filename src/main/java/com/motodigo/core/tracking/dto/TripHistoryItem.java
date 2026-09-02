package com.motodigo.core.tracking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TripHistoryItem(
		UUID id,
		String kind,
		String tripType,
		String status,
		String origin,
		String destination,
		BigDecimal amount,
		String paymentStatus,
		UUID receiptId,
		boolean ratingSubmitted,
		Instant createdAt,
		Instant completedAt) {
}
