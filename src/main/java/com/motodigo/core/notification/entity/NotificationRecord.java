package com.motodigo.core.notification.entity;

import java.time.Instant;
import java.util.UUID;

public record NotificationRecord(
		UUID id,
		UUID userId,
		String type,
		String title,
		String body,
		String status,
		Instant scheduledAt,
		Instant sentAt,
		Instant readAt,
		UUID bookingId,
		UUID paymentTransactionId,
		UUID tripId,
		Instant createdAt,
		String metadataJson) {

	public boolean unread() {
		return this.readAt == null;
	}
}
