package com.motodigo.core.booking.dto;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.booking.entity.BookingRecord;

public record BookingResponse(
		UUID id,
		UUID announcementId,
		UUID passengerProfileId,
		UUID tripId,
		String status,
		int seatsRequested,
		String contactPhone,
		Instant requestedAt,
		Instant confirmedAt,
		Instant cancelledAt,
		Instant expiresAt,
		String cancellationReason,
		String passengerDisplayName) {

	public static BookingResponse from(BookingRecord booking) {
		return from(booking, null);
	}

	public static BookingResponse from(BookingRecord booking, String passengerDisplayName) {
		return new BookingResponse(
				booking.id(),
				booking.announcementId(),
				booking.passengerProfileId(),
				booking.tripId(),
				booking.status().name(),
				booking.seatsRequested(),
				booking.contactPhone(),
				booking.requestedAt(),
				booking.confirmedAt(),
				booking.cancelledAt(),
				booking.expiresAt(),
				booking.cancellationReason(),
				passengerDisplayName);
	}
}
