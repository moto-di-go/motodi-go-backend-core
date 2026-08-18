package com.motodigo.core.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateBookingRequest(
		@NotNull @Positive Integer seatsRequested,
		@NotBlank @Pattern(regexp = "^\\+237[6-9]\\d{8}$") String contactPhone) {
}
