package com.motodigo.core.tracking.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record UpdateTripStatusRequest(
		@NotBlank String status) {
}
