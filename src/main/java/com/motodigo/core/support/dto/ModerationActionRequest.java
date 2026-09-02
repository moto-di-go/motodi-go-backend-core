package com.motodigo.core.support.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ModerationActionRequest(
		@NotBlank String targetType,
		@NotNull UUID targetId,
		@NotBlank String action,
		String reason) {
}
