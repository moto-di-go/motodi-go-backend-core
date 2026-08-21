package com.motodigo.core.tracking.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TripRecord(
		UUID id,
		TripType type,
		TripStatus status,
		UUID passengerProfileId,
		UUID driverProfileId,
		UUID transporterProfileId,
		UUID routeId,
		UUID fareEstimateId,
		Double originLat,
		Double originLon,
		Double destinationLat,
		Double destinationLon,
		Instant plannedDepartureAt,
		Instant startedAt,
		Instant completedAt,
		BigDecimal finalPriceFcfa,
		String cancellationReason,
		Instant createdAt,
		UUID bookingId,
		UUID announcementPublisherProfileId,
		String departureCity,
		String destinationCity,
		String ptRamassage) {
}
