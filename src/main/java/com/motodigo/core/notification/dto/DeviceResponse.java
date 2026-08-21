package com.motodigo.core.notification.dto;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.notification.entity.DevicePlatform;
import com.motodigo.core.notification.entity.NotificationDeviceRecord;

public record DeviceResponse(
		UUID id,
		String token,
		DevicePlatform platform,
		Instant updatedAt) {

	public static DeviceResponse from(NotificationDeviceRecord record) {
		return new DeviceResponse(record.id(), record.token(), record.platform(), record.updatedAt());
	}
}
