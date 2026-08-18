package com.motodigo.core.geo.dto;

import com.motodigo.core.profile.entity.DriverAvailability;
import jakarta.validation.constraints.NotNull;

public record DriverAvailabilityRequest(
		@NotNull DriverAvailability availability,
		Boolean gpsConsent) {
}
