package com.motodigo.core.profile.service;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.profile.dto.CreateProfileRequest;
import com.motodigo.core.profile.dto.ProfileResponse;
import com.motodigo.core.profile.dto.UpdateProfileRequest;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import com.motodigo.core.profile.repository.ProfileRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ProfileService {

	private final ProfileRepository profileRepository;
	private final PermissionService permissionService;

	public ProfileService(ProfileRepository profileRepository, PermissionService permissionService) {
		this.profileRepository = profileRepository;
		this.permissionService = permissionService;
	}

	@Transactional(readOnly = true)
	public List<ProfileResponse> listProfiles(UUID userId) {
		return this.profileRepository.findByUserId(userId).stream()
				.map(this::response)
				.toList();
	}

	@Transactional(readOnly = true)
	public ProfileResponse currentProfile(UUID userId) {
		return this.profileRepository.findActiveByUserId(userId)
				.map(this::response)
				.orElseThrow(() -> new AuthException(AuthErrorCode.PROFILE_NOT_FOUND));
	}

	@Transactional
	public ProfileResponse createProfile(UUID userId, CreateProfileRequest request) {
		validateCreate(request);
		boolean active = !this.profileRepository.existsAnyByUserId(userId);
		try {
			ProfileRecord profile = this.profileRepository.create(userId, request, initialStatus(request.type()), active);
			return response(profile);
		}
		catch (DuplicateKeyException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains("uq_profiles_user_type")) {
				throw new AuthException(AuthErrorCode.PROFILE_ALREADY_EXISTS);
			}
			throw new AuthException(AuthErrorCode.PROFILE_RULE_VIOLATION);
		}
	}

	@Transactional
	public ProfileResponse updateProfile(UUID userId, UUID profileId, UpdateProfileRequest request) {
		ProfileRecord existing = this.profileRepository.findOwnedById(userId, profileId)
				.orElseThrow(() -> new AuthException(AuthErrorCode.PROFILE_NOT_FOUND));
		validateUpdate(existing, request);
		return response(this.profileRepository.update(userId, existing, request));
	}

	@Transactional
	public ProfileResponse activateProfile(UUID userId, UUID profileId) {
		ProfileRecord profile = this.profileRepository.findOwnedById(userId, profileId)
				.orElseThrow(() -> new AuthException(AuthErrorCode.PROFILE_NOT_FOUND));
		if (profile.validationStatus() == ValidationStatus.SUSPENDED) {
			throw new AuthException(AuthErrorCode.PROFILE_NOT_ALLOWED);
		}
		this.profileRepository.clearActive(userId);
		this.profileRepository.markActive(userId, profileId);
		return this.profileRepository.findOwnedById(userId, profileId)
				.map(this::response)
				.orElseThrow(() -> new AuthException(AuthErrorCode.PROFILE_NOT_FOUND));
	}

	private ProfileResponse response(ProfileRecord profile) {
		return ProfileResponse.from(profile, this.permissionService.allowedActions(profile));
	}

	private static ValidationStatus initialStatus(ProfileType type) {
		return ValidationStatus.PENDING;
	}

	private static void validateCreate(CreateProfileRequest request) {
		if (request.type() == ProfileType.ADMIN) {
			throw new AuthException(AuthErrorCode.PROFILE_NOT_ALLOWED);
		}
		if (isBlank(request.displayName())) {
			throw new AuthException(AuthErrorCode.PROFILE_RULE_VIOLATION);
		}
		validateRelevantDetails(request.type(), request.passenger() != null, request.driver() != null,
				request.motoTaxi() != null, request.transporter() != null, request.agency() != null);
		if (request.type() == ProfileType.AGENCY && (request.agency() == null || isBlank(request.agency().legalName()))) {
			throw new AuthException(AuthErrorCode.PROFILE_RULE_VIOLATION);
		}
	}

	private static void validateUpdate(ProfileRecord existing, UpdateProfileRequest request) {
		if (request.displayName() != null && isBlank(request.displayName())) {
			throw new AuthException(AuthErrorCode.PROFILE_RULE_VIOLATION);
		}
		validateRelevantDetails(existing.type(), request.passenger() != null, request.driver() != null,
				request.motoTaxi() != null, request.transporter() != null, request.agency() != null);
		if (existing.type() == ProfileType.AGENCY && request.agency() != null && request.agency().legalName() != null
				&& isBlank(request.agency().legalName())) {
			throw new AuthException(AuthErrorCode.PROFILE_RULE_VIOLATION);
		}
	}

	private static void validateRelevantDetails(ProfileType type, boolean passenger, boolean driver, boolean motoTaxi,
			boolean transporter, boolean agency) {
		boolean valid = switch (type) {
			case PASSENGER -> passenger || !(driver || motoTaxi || transporter || agency);
			case MOTOTAXI -> !passenger && !transporter && !agency;
			case TRANSPORTER -> !passenger && !motoTaxi && !agency;
			case AGENCY -> agency || !(passenger || driver || motoTaxi || transporter);
			case ADMIN -> false;
		};
		if (!valid) {
			throw new AuthException(AuthErrorCode.PROFILE_RULE_VIOLATION);
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
