package com.motodigo.core.notification.dto;

import com.motodigo.core.notification.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterDeviceRequest(
		@NotBlank String token,
		@NotNull DevicePlatform platform) {
}
