package com.motodigo.core.tracking.dto;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.tracking.entity.TripEventRecord;

public record TripEventResponse(
		UUID id,
		UUID tripId,
		String status,
		String label,
		Instant occurredAt,
		String source) {

	public static TripEventResponse from(TripEventRecord event) {
		return new TripEventResponse(
				event.id(),
				event.tripId(),
				event.status().name(),
				event.label(),
				event.occurredAt(),
				event.source());
	}
}
