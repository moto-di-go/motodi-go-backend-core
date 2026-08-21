package com.motodigo.core.tracking.dto;

import java.util.List;

public record ActiveTripsResponse(
		List<TripResponse> trips) {
}
