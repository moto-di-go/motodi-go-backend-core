package com.motodigo.core.auth.dto;

import java.time.Instant;

public record ErrorResponse(
		String errorCode,
		String message,
		String correlationId,
		Instant timestamp) {
}
