package com.motodigo.core.profile.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.profile.entity.DriverAvailability;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;

public record ProfileResponse(
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
		PassengerDetailsResponse passenger,
		DriverDetailsResponse driver,
		MotoTaxiDetailsResponse motoTaxi,
		TransporterDetailsResponse transporter,
		AgencyDetailsResponse agency,
		AdminDetailsResponse admin,
		List<String> allowedActions) {

	public static ProfileResponse from(ProfileRecord profile, List<String> allowedActions) {
		return new ProfileResponse(
				profile.id(),
				profile.userId(),
				profile.type(),
				profile.validationStatus(),
				profile.displayName(),
				profile.avatarUrl(),
				profile.averageRating(),
				profile.active(),
				profile.createdAt(),
				profile.updatedAt(),
				profile.passenger() == null ? null : new PassengerDetailsResponse(
						profile.passenger().preferredLanguage(),
						profile.passenger().emergencyContact()),
				profile.driver() == null ? null : new DriverDetailsResponse(
						profile.driver().availability(),
						profile.driver().gpsConsent(),
						profile.driver().lastAvailabilityChangeAt()),
				profile.motoTaxi() == null ? null : new MotoTaxiDetailsResponse(
						profile.motoTaxi().licenseNumber(),
						profile.motoTaxi().operatingZone()),
				profile.transporter() == null ? null : new TransporterDetailsResponse(
						profile.transporter().permitNumber(),
						profile.transporter().companyName()),
				profile.agency() == null ? null : new AgencyDetailsResponse(
						profile.agency().legalName(),
						profile.agency().tradeRegisterNumber(),
						profile.agency().billingPlan()),
				profile.admin() == null ? null : new AdminDetailsResponse(
						profile.admin().adminRole(),
						profile.admin().permissions()),
				allowedActions);
	}

	public record PassengerDetailsResponse(String preferredLanguage, String emergencyContact) {
	}

	public record DriverDetailsResponse(DriverAvailability availability, boolean gpsConsent, Instant lastAvailabilityChangeAt) {
	}

	public record MotoTaxiDetailsResponse(String licenseNumber, String operatingZone) {
	}

	public record TransporterDetailsResponse(String permitNumber, String companyName) {
	}

	public record AgencyDetailsResponse(String legalName, String tradeRegisterNumber, String billingPlan) {
	}

	public record AdminDetailsResponse(String adminRole, List<String> permissions) {
	}
}
