package com.motodigo.core.tracking.entity;

import java.time.Instant;
import java.util.UUID;

public record TripEventRecord(
		UUID id,
		UUID tripId,
		TripStatus status,
		String label,
		Instant occurredAt,
		String source) {
}
