package com.motodigo.core.tracking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.geo.dto.DriverPositionResponse;
import com.motodigo.core.tracking.entity.TripRecord;

public record TripResponse(
		UUID id,
		String type,
		String status,
		UUID passengerProfileId,
		UUID driverProfileId,
		UUID transporterProfileId,
		UUID bookingId,
		UUID announcementPublisherProfileId,
		String departureCity,
		String destinationCity,
		String ptRamassage,
		Instant plannedDepartureAt,
		Instant startedAt,
		Instant completedAt,
		BigDecimal finalPriceFcfa,
		String cancellationReason,
		Instant createdAt,
		DriverPositionResponse driverPosition) {

	public static TripResponse from(TripRecord trip) {
		return from(trip, null);
	}

	public static TripResponse from(TripRecord trip, DriverPositionResponse driverPosition) {
		return new TripResponse(
				trip.id(),
				trip.type().name(),
				trip.status().name(),
				trip.passengerProfileId(),
				trip.driverProfileId(),
				trip.transporterProfileId(),
				trip.bookingId(),
				trip.announcementPublisherProfileId(),
				trip.departureCity(),
				trip.destinationCity(),
				trip.ptRamassage(),
				trip.plannedDepartureAt(),
				trip.startedAt(),
				trip.completedAt(),
				trip.finalPriceFcfa(),
				trip.cancellationReason(),
				trip.createdAt(),
				driverPosition);
	}
}
