package com.motodigo.core.notification.entity;

import java.time.Instant;
import java.util.UUID;

public record NotificationDeviceRecord(
		UUID id,
		UUID userId,
		String token,
		DevicePlatform platform,
		Instant updatedAt,
		Instant createdAt) {
}
