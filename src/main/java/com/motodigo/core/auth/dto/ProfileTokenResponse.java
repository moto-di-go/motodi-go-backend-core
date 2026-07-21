package com.motodigo.core.auth.dto;

import java.util.UUID;

public record ProfileTokenResponse(
		UUID id,
		String type,
		String validationStatus,
		boolean active) {
}
