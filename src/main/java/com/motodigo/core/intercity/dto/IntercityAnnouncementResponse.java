package com.motodigo.core.intercity.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.motodigo.core.intercity.entity.IntercityAnnouncementRecord;

public record IntercityAnnouncementResponse(
		UUID id,
		UUID publisherProfileId,
		UUID vehicleId,
		String departureCity,
		String destinationCity,
		String departureQuarter,
		LocalDate departureDate,
		LocalTime departureTime,
		BigDecimal priceFcfa,
		int totalSeats,
		int remainingSeats,
		String instructions,
		String status,
		Instant createdAt,
		Instant updatedAt) {

	public static IntercityAnnouncementResponse from(IntercityAnnouncementRecord record) {
		return new IntercityAnnouncementResponse(
				record.id(),
				record.publisherProfileId(),
				record.vehicleId(),
				record.departureCity(),
				record.destinationCity(),
				record.departureQuarter(),
				record.departureDate(),
				record.departureTime(),
				record.priceFcfa(),
				record.totalSeats(),
				record.remainingSeats(),
				record.instructions(),
				record.status().name(),
				record.createdAt(),
				record.updatedAt());
	}
}
