package com.motodigo.core.agency.dto;

import java.time.Instant;
import java.util.UUID;

public record AgencyBookingSummaryResponse(
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
