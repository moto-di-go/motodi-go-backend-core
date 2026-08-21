package com.motodigo.core.notification.dto;

import java.time.Instant;

import com.motodigo.core.notification.entity.NotificationPreferenceRecord;

public record NotificationPreferencesResponse(
		boolean pushEnabled,
		boolean bookingUpdates,
		boolean paymentAlerts,
		boolean marketingEnabled,
		Instant updatedAt) {

	public static NotificationPreferencesResponse from(NotificationPreferenceRecord record) {
		return new NotificationPreferencesResponse(
				record.pushEnabled(),
				record.bookingUpdates(),
				record.paymentAlerts(),
				record.marketingEnabled(),
				record.updatedAt());
	}
}
