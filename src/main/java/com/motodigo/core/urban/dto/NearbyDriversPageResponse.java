package com.motodigo.core.urban.dto;

import java.util.List;

public record NearbyDriversPageResponse(
		List<NearbyDriverResponse> items,
		String emptyReason,
		double radiusKm) {
}
