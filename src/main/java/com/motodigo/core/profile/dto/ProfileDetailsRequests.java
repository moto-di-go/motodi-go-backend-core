package com.motodigo.core.profile.dto;

import jakarta.validation.constraints.Size;

public final class ProfileDetailsRequests {

	private ProfileDetailsRequests() {
	}

	public record PassengerDetailsRequest(
			@Size(max = 16) String preferredLanguage,
			@Size(max = 20) String emergencyContact) {
	}

	public record DriverDetailsRequest(Boolean gpsConsent) {
	}

	public record MotoTaxiDetailsRequest(
			@Size(max = 80) String licenseNumber,
			@Size(max = 160) String operatingZone) {
	}

	public record TransporterDetailsRequest(
			@Size(max = 80) String permitNumber,
			@Size(max = 160) String companyName) {
	}

	public record AgencyDetailsRequest(
			@Size(max = 180) String legalName,
			@Size(max = 80) String tradeRegisterNumber,
			@Size(max = 80) String billingPlan) {
	}
}
