package com.motodigo.core.intercity.dto;

import java.util.UUID;

import com.motodigo.core.intercity.entity.VehicleRecord;

public record VehicleResponse(
		UUID id,
		UUID ownerProfileId,
		String type,
		Integer capacity,
		String validationStatus,
		boolean active,
		String photoUrl) {

	public static VehicleResponse from(VehicleRecord vehicle) {
		return new VehicleResponse(
				vehicle.id(),
				vehicle.ownerProfileId(),
				vehicle.type(),
				vehicle.capacity(),
				vehicle.validationStatus(),
				vehicle.active(),
				"/vehicles/" + vehicle.id() + "/photo");
	}
}
