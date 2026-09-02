package com.motodigo.core.rating.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RatingRequest(
		@NotNull UUID ratedProfileId,
		@NotNull @Min(1) @Max(5) Integer stars,
		@Size(max = 1000) String comment,
		List<String> tags) {
}
