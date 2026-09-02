package com.motodigo.core.rating.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.rating.entity.RatingRecord;

public record RatingResponse(
		UUID id,
		UUID tripId,
		UUID ratedProfileId,
		int stars,
		String comment,
		List<String> tags,
		String status,
		BigDecimal averageRating,
		Instant createdAt) {

	public static RatingResponse from(RatingRecord rating, BigDecimal averageRating) {
		return new RatingResponse(
				rating.id(),
				rating.tripId(),
				rating.ratedProfileId(),
				rating.stars(),
				rating.comment(),
				rating.tags() == null ? List.of() : rating.tags(),
				rating.status().name(),
				averageRating,
				rating.createdAt());
	}
}
