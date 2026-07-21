package com.motodigo.core.profile.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.profile.dto.CreateProfileRequest;
import com.motodigo.core.profile.dto.ProfileDetailsRequests.AgencyDetailsRequest;
import com.motodigo.core.profile.dto.ProfileDetailsRequests.PassengerDetailsRequest;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import com.motodigo.core.profile.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileServiceTest {

	@Mock
	private ProfileRepository profileRepository;

	@Mock
	private PermissionService permissionService;

	private ProfileService profileService;

	@BeforeEach
	void setUp() {
		this.profileService = new ProfileService(this.profileRepository, this.permissionService);
		when(this.permissionService.allowedActions(any())).thenReturn(List.of("PROFILE_READ"));
	}

	@Test
	void createPassengerUsesValidatedStatusAndAllowsSeveralCompatibleProfiles() {
		UUID userId = UUID.randomUUID();
		CreateProfileRequest request = new CreateProfileRequest(ProfileType.PASSENGER, "Aline", null,
				new PassengerDetailsRequest("fr", null), null, null, null, null);
		when(this.profileRepository.existsAnyByUserId(userId)).thenReturn(false);
		when(this.profileRepository.create(eq(userId), eq(request), eq(ValidationStatus.VALIDATED), eq(true)))
				.thenReturn(profile(userId, ProfileType.PASSENGER, ValidationStatus.VALIDATED, true));

		this.profileService.createProfile(userId, request);

		verify(this.profileRepository).create(eq(userId), eq(request), eq(ValidationStatus.VALIDATED), eq(true));
	}

	@Test
	void duplicateProfileTypeReturnsStableConflict() {
		UUID userId = UUID.randomUUID();
		CreateProfileRequest request = new CreateProfileRequest(ProfileType.PASSENGER, "Aline", null, null, null, null, null,
				null);
		when(this.profileRepository.create(eq(userId), eq(request), eq(ValidationStatus.VALIDATED), eq(false)))
				.thenThrow(new DuplicateKeyException("uq_profiles_user_type"));
		when(this.profileRepository.existsAnyByUserId(userId)).thenReturn(true);

		assertThatThrownBy(() -> this.profileService.createProfile(userId, request))
				.isInstanceOf(AuthException.class)
				.extracting("errorCode")
				.isEqualTo(AuthErrorCode.PROFILE_ALREADY_EXISTS);
	}

	@Test
	void adminSelfServiceCreationIsForbidden() {
		CreateProfileRequest request = new CreateProfileRequest(ProfileType.ADMIN, "Admin", null, null, null, null, null, null);

		assertThatThrownBy(() -> this.profileService.createProfile(UUID.randomUUID(), request))
				.isInstanceOf(AuthException.class)
				.extracting("errorCode")
				.isEqualTo(AuthErrorCode.PROFILE_NOT_ALLOWED);
	}

	@Test
	void agencyCreationRequiresLegalName() {
		CreateProfileRequest request = new CreateProfileRequest(ProfileType.AGENCY, "Agency", null, null, null, null, null,
				new AgencyDetailsRequest(" ", "RC", null));

		assertThatThrownBy(() -> this.profileService.createProfile(UUID.randomUUID(), request))
				.isInstanceOf(AuthException.class)
				.extracting("errorCode")
				.isEqualTo(AuthErrorCode.PROFILE_RULE_VIOLATION);
	}

	@Test
	void suspendedProfileCannotBeActivated() {
		UUID userId = UUID.randomUUID();
		UUID profileId = UUID.randomUUID();
		when(this.profileRepository.findOwnedById(userId, profileId))
				.thenReturn(Optional.of(profile(userId, ProfileType.MOTOTAXI, ValidationStatus.SUSPENDED, false)));

		assertThatThrownBy(() -> this.profileService.activateProfile(userId, profileId))
				.isInstanceOf(AuthException.class)
				.extracting("errorCode")
				.isEqualTo(AuthErrorCode.PROFILE_NOT_ALLOWED);
	}

	private static ProfileRecord profile(UUID userId, ProfileType type, ValidationStatus status, boolean active) {
		return new ProfileRecord(UUID.randomUUID(), userId, type, status, "Aline", null, java.math.BigDecimal.ZERO, active,
				Instant.now(), Instant.now(), null, null, null, null, null, null);
	}
}
