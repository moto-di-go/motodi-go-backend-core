package com.motodigo.core.rating.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RatingRecord(
		UUID id,
		UUID tripId,
		UUID raterUserId,
		UUID ratedProfileId,
		int stars,
		String comment,
		List<String> tags,
		RatingStatus status,
		Instant createdAt) {
}
