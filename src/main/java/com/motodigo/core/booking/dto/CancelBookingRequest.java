package com.motodigo.core.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelBookingRequest(
		@NotBlank @Size(max = 500) String cancellationReason) {
}
