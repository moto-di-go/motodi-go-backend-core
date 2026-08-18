package com.motodigo.core.urban.entity;

import java.util.UUID;

public record UrbanPlaceRecord(
		UUID id,
		String label,
		double lat,
		double lon,
		String kind,
		boolean inServiceArea,
		int sortOrder) {
}
