package com.motodigo.core.geo.entity;

import java.time.Instant;
import java.util.UUID;

public record DriverPositionRecord(
		UUID id,
		UUID driverProfileId,
		double lat,
		double lon,
		Double speedKmh,
		Double accuracyMeters,
		Instant capturedAt,
		Instant receivedAt,
		boolean consentConfirmed) {
}
