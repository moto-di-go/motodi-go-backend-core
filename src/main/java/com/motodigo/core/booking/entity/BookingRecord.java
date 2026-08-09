package com.motodigo.core.booking.entity;

import java.time.Instant;
import java.util.UUID;

public record BookingRecord(
		UUID id,
		UUID announcementId,
		UUID passengerProfileId,
		UUID tripId,
		BookingStatus status,
		int seatsRequested,
		Instant requestedAt,
		Instant confirmedAt,
		Instant cancelledAt,
		Instant expiresAt,
		String cancellationReason) {
}
