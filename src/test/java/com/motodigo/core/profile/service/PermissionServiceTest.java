package com.motodigo.core.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import org.junit.jupiter.api.Test;

class PermissionServiceTest {

	private final PermissionService permissionService = new PermissionService();

	@Test
	void passengerReceivesPassengerActions() {
		assertThat(this.permissionService.allowedActions(profile(ProfileType.PASSENGER, ValidationStatus.VALIDATED)))
				.contains("PROFILE_READ", "PROFILE_UPDATE", "PROFILE_SWITCH", "PASSENGER_SEARCH_RIDE", "PASSENGER_BOOK_RIDE",
						"PASSENGER_PAY");
	}

	@Test
	void pendingDriverReceivesKycUploadAndValidatedDriverReceivesOperationalActions() {
		assertThat(this.permissionService.allowedActions(profile(ProfileType.MOTOTAXI, ValidationStatus.PENDING)))
				.contains("KYC_UPLOAD")
				.doesNotContain("MOTOTAXI_SHARE_POSITION");

		assertThat(this.permissionService.allowedActions(profile(ProfileType.MOTOTAXI, ValidationStatus.VALIDATED)))
				.contains("MOTOTAXI_SET_AVAILABILITY", "MOTOTAXI_SHARE_POSITION", "MOTOTAXI_MANAGE_TRIP")
				.doesNotContain("KYC_UPLOAD");
	}

	private static ProfileRecord profile(ProfileType type, ValidationStatus status) {
		return new ProfileRecord(UUID.randomUUID(), UUID.randomUUID(), type, status, "Profile", null, BigDecimal.ZERO, true,
				Instant.now(), Instant.now(), null, null, null, null, null, null);
	}
}
