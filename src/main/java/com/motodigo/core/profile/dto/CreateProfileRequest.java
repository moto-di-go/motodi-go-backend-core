package com.motodigo.core.profile.dto;

import com.motodigo.core.profile.dto.ProfileDetailsRequests.AgencyDetailsRequest;
import com.motodigo.core.profile.dto.ProfileDetailsRequests.DriverDetailsRequest;
import com.motodigo.core.profile.dto.ProfileDetailsRequests.MotoTaxiDetailsRequest;
import com.motodigo.core.profile.dto.ProfileDetailsRequests.PassengerDetailsRequest;
import com.motodigo.core.profile.dto.ProfileDetailsRequests.TransporterDetailsRequest;
import com.motodigo.core.profile.entity.ProfileType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProfileRequest(
		@NotNull ProfileType type,
		@NotBlank @Size(max = 120) String displayName,
		@Size(max = 2048) String avatarUrl,
		@Valid PassengerDetailsRequest passenger,
		@Valid DriverDetailsRequest driver,
		@Valid MotoTaxiDetailsRequest motoTaxi,
		@Valid TransporterDetailsRequest transporter,
		@Valid AgencyDetailsRequest agency) {
}
