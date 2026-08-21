package com.motodigo.core.ops.dto;

import java.time.Instant;
import java.util.UUID;

public record OpsBookingSummaryResponse(
		UUID id,
		UUID announcementId,
		UUID passengerProfileId,
		String status,
		int seatsRequested,
		String contactPhone,
		Instant requestedAt,
		Instant confirmedAt,
		Instant cancelledAt,
		Instant expiresAt) {
}
