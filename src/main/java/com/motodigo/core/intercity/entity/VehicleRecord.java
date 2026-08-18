package com.motodigo.core.intercity.entity;

import java.util.UUID;

public record VehicleRecord(
		UUID id,
		UUID ownerProfileId,
		String type,
		int capacity,
		String validationStatus,
		boolean active) {
}
