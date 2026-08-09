package com.motodigo.core.booking.entity;

import java.time.Instant;
import java.util.UUID;

public record SeatLockRecord(
		UUID id,
		UUID announcementId,
		UUID bookingId,
		int seatsLocked,
		Instant lockedAt,
		Instant expiresAt,
		boolean consumed) {
}
