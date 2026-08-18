package com.motodigo.core.geo.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record RouteMatchRequest(
		@NotEmpty List<@Valid RouteMatchPoint> points) {

	public record RouteMatchPoint(
			@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
			@NotNull @DecimalMin("-180") @DecimalMax("180") Double lon) {
	}
}
