package com.motodigo.core.geo.dto;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.profile.entity.DriverAvailability;

public record DriverAvailabilityResponse(
		UUID driverProfileId,
		DriverAvailability availability,
		boolean gpsConsent,
		Instant lastAvailabilityChangeAt) {
}
