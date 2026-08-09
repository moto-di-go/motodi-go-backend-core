package com.motodigo.core.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateBookingRequest(
		@NotNull @Positive Integer seatsRequested) {
}
