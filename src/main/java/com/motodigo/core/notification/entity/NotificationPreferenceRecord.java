package com.motodigo.core.notification.entity;

import java.time.Instant;
import java.util.UUID;

public record NotificationPreferenceRecord(
		UUID id,
		UUID userId,
		boolean pushEnabled,
		boolean bookingUpdates,
		boolean paymentAlerts,
		boolean marketingEnabled,
		Instant updatedAt) {
}
