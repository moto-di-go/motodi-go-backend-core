package com.motodigo.core.profile.dto;

import com.motodigo.core.profile.dto.ProfileDetailsRequests.AgencyDetailsRequest;
import com.motodigo.core.profile.dto.ProfileDetailsRequests.DriverDetailsRequest;
import com.motodigo.core.profile.dto.ProfileDetailsRequests.MotoTaxiDetailsRequest;
import com.motodigo.core.profile.dto.ProfileDetailsRequests.PassengerDetailsRequest;
import com.motodigo.core.profile.dto.ProfileDetailsRequests.TransporterDetailsRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
		@Size(max = 120) String displayName,
		@Size(max = 2048) String avatarUrl,
		@Valid PassengerDetailsRequest passenger,
		@Valid DriverDetailsRequest driver,
		@Valid MotoTaxiDetailsRequest motoTaxi,
		@Valid TransporterDetailsRequest transporter,
		@Valid AgencyDetailsRequest agency) {
}
