package com.motodigo.core.agency.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AgencyAnnouncementSummaryResponse(
		UUID id,
		UUID publisherProfileId,
		String departureCity,
		String destinationCity,
		LocalDate departureDate,
		LocalTime departureTime,
		BigDecimal priceFcfa,
		int totalSeats,
		int remainingSeats,
		String status,
		Instant createdAt) {
}
