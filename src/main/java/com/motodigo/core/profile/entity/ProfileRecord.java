package com.motodigo.core.profile.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProfileRecord(
		UUID id,
		UUID userId,
		ProfileType type,
		ValidationStatus validationStatus,
		String displayName,
		String avatarUrl,
		BigDecimal averageRating,
		boolean active,
		Instant createdAt,
		Instant updatedAt,
		PassengerDetails passenger,
		DriverDetails driver,
		MotoTaxiDetails motoTaxi,
		TransporterDetails transporter,
		AgencyDetails agency,
		AdminDetails admin) {

	public record PassengerDetails(String preferredLanguage, String emergencyContact) {
	}

	public record DriverDetails(DriverAvailability availability, boolean gpsConsent, Instant lastAvailabilityChangeAt) {
	}

	public record MotoTaxiDetails(String licenseNumber, String operatingZone) {
	}

	public record TransporterDetails(String permitNumber, String companyName) {
	}

	public record AgencyDetails(String legalName, String tradeRegisterNumber, String billingPlan) {
	}

	public record AdminDetails(String adminRole, List<String> permissions) {
	}
}
