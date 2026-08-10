package com.motodigo.core.intercity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateIntercityAnnouncementRequest(
		@Size(max = 120) String departureCity,
		@Size(max = 120) String destinationCity,
		@Size(max = 120) String ptRamassage,
		@Valid GeoPointRequest departurePoint,
		@Valid GeoPointRequest destinationPoint,
		LocalDate departureDate,
		LocalTime departureTime,
		@DecimalMin("0.0") BigDecimal priceFcfa,
		@Positive Integer totalSeats,
		UUID vehicleId,
		@Size(max = 2000) String instructions) {
}
