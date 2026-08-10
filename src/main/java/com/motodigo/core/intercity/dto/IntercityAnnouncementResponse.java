package com.motodigo.core.intercity.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.motodigo.core.intercity.entity.IntercityAnnouncementRecord;
import com.motodigo.core.intercity.entity.VehicleRecord;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ValidationStatus;

public record IntercityAnnouncementResponse(
		UUID id,
		UUID publisherProfileId,
		UUID vehicleId,
		String departureCity,
		String destinationCity,
		String ptRamassage,
		LocalDate departureDate,
		LocalTime departureTime,
		BigDecimal priceFcfa,
		int totalSeats,
		int remainingSeats,
		String instructions,
		String status,
		Instant createdAt,
		Instant updatedAt,
		String publisherDisplayName,
		boolean publisherVerified,
		String vehicleType,
		Integer vehicleCapacity,
		Double departureLat,
		Double departureLon,
		String vehiclePhotoUrl) {

	public static IntercityAnnouncementResponse from(IntercityAnnouncementRecord record) {
		return from(record, null, null, null, null);
	}

	public static IntercityAnnouncementResponse from(
			IntercityAnnouncementRecord record,
			ProfileRecord publisher,
			VehicleRecord vehicle,
			Double departureLat,
			Double departureLon) {
		String displayName = publisher == null ? null : publisher.displayName();
		boolean verified = publisher != null && publisher.validationStatus() == ValidationStatus.VALIDATED;
		String vehicleType = vehicle == null ? null : vehicle.type();
		Integer vehicleCapacity = vehicle == null ? null : vehicle.capacity();
				String photoUrl = record.vehicleId() == null ? null : "/vehicles/" + record.vehicleId() + "/photo";
		return new IntercityAnnouncementResponse(
				record.id(),
				record.publisherProfileId(),
				record.vehicleId(),
				record.departureCity(),
				record.destinationCity(),
				record.ptRamassage(),
				record.departureDate(),
				record.departureTime(),
				record.priceFcfa(),
				record.totalSeats(),
				record.remainingSeats(),
				record.instructions(),
				record.status().name(),
				record.createdAt(),
				record.updatedAt(),
				displayName,
				verified,
				vehicleType,
				vehicleCapacity,
				departureLat,
				departureLon,
				photoUrl);
	}
}
