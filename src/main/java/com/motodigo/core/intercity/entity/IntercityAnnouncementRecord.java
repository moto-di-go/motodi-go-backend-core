package com.motodigo.core.intercity.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record IntercityAnnouncementRecord(
		UUID id,
		UUID publisherProfileId,
		UUID vehicleId,
		UUID routeId,
		String departureCity,
		String destinationCity,
		String departureQuarter,
		LocalDate departureDate,
		LocalTime departureTime,
		BigDecimal priceFcfa,
		int totalSeats,
		int remainingSeats,
		String instructions,
		AnnouncementStatus status,
		Instant createdAt,
		Instant updatedAt) {
}
