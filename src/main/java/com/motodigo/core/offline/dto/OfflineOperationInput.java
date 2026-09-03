package com.motodigo.core.offline.dto;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OfflineOperationInput(
		@NotBlank String type,
		@NotBlank @Size(max = 180) String idempotencyKey,
		@NotNull Map<String, Object> payload,
		Instant queuedAt) {
}
