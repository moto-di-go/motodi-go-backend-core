package com.motodigo.core.intercity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateIntercityAnnouncementRequest(
		@NotBlank @Size(max = 120) String departureCity,
		@NotBlank @Size(max = 120) String destinationCity,
		@Size(max = 120) String ptRamassage,
		@Valid GeoPointRequest departurePoint,
		@Valid GeoPointRequest destinationPoint,
		@NotNull LocalDate departureDate,
		@NotNull LocalTime departureTime,
		@NotNull @DecimalMin("0.0") BigDecimal priceFcfa,
		@NotNull @Positive Integer totalSeats,
		@NotNull UUID vehicleId,
		@Size(max = 2000) String instructions) {
}
