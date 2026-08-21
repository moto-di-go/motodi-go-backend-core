package com.motodigo.core.notification.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferencesRequest(
		@NotNull Boolean pushEnabled,
		@NotNull Boolean bookingUpdates,
		@NotNull Boolean paymentAlerts,
		@NotNull Boolean marketingEnabled) {
}
