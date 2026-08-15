package com.motodigo.core.urban.dto;

import java.util.UUID;

public record UrbanPlaceResponse(
		UUID id,
		String label,
		double lat,
		double lon,
		String kind) {
}
