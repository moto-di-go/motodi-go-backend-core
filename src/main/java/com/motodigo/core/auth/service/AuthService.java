package com.motodigo.core.auth.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.auth.dto.AuthTokenResponse;
import com.motodigo.core.auth.dto.LoginRequest;
import com.motodigo.core.auth.dto.OtpResendRequest;
import com.motodigo.core.auth.dto.OtpResendResponse;
import com.motodigo.core.auth.dto.OtpVerifyRequest;
import com.motodigo.core.auth.dto.OtpVerifyResponse;
import com.motodigo.core.auth.dto.ProfileTokenResponse;
import com.motodigo.core.auth.dto.RefreshRequest;
import com.motodigo.core.auth.dto.RegisterRequest;
import com.motodigo.core.auth.dto.RegisterResponse;
import com.motodigo.core.auth.dto.ResetPasswordRequest;
import com.motodigo.core.auth.entity.AuthEventType;
import com.motodigo.core.auth.entity.OtpPurpose;
import com.motodigo.core.auth.entity.UserRecord;
import com.motodigo.core.auth.entity.UserStatus;
import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.auth.repository.AuthAttemptEventRepository;
import com.motodigo.core.auth.repository.SessionRepository;
import com.motodigo.core.auth.repository.UserRepository;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.repository.ProfileRepository;
import com.motodigo.core.profile.service.PermissionService;
import com.motodigo.core.wallet.WalletProvisioningRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AuthService {

	private final UserRepository userRepository;
	private final SessionRepository sessionRepository;
	private final WalletProvisioningRepository walletProvisioningRepository;
	private final AuthAttemptEventRepository authAttemptEventRepository;
	private final ProfileRepository profileRepository;
	private final PermissionService permissionService;
	private final OtpService otpService;
	private final TokenService tokenService;
	private final PhoneNumberNormalizer phoneNumberNormalizer;
	private final Sha256TokenHasher hasher;
	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository userRepository, SessionRepository sessionRepository,
			WalletProvisioningRepository walletProvisioningRepository, AuthAttemptEventRepository authAttemptEventRepository,
			ProfileRepository profileRepository, PermissionService permissionService, OtpService otpService,
			TokenService tokenService, PhoneNumberNormalizer phoneNumberNormalizer, Sha256TokenHasher hasher,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.sessionRepository = sessionRepository;
		this.walletProvisioningRepository = walletProvisioningRepository;
		this.authAttemptEventRepository = authAttemptEventRepository;
		this.profileRepository = profileRepository;
		this.permissionService = permissionService;
		this.otpService = otpService;
		this.tokenService = tokenService;
		this.phoneNumberNormalizer = phoneNumberNormalizer;
		this.hasher = hasher;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public RegisterResponse register(RegisterRequest request, RequestMetadata metadata) {
		String phoneNumber = this.phoneNumberNormalizer.normalize(request.phoneNumber());
		if (this.userRepository.existsByPhoneNumber(phoneNumber)) {
			this.authAttemptEventRepository.save(null, phoneNumber, AuthEventType.REGISTER, false,
					AuthErrorCode.PHONE_ALREADY_EXISTS.name(), metadata);
			throw new AuthException(AuthErrorCode.PHONE_ALREADY_EXISTS);
		}

		UserRecord user = this.userRepository.savePending(
				phoneNumber,
				this.passwordEncoder.encode(request.password()),
				request.fullName(),
				request.email(),
				request.locale());
		var otp = this.otpService.issueOtp(user.id(), phoneNumber, OtpPurpose.REGISTRATION, metadata);
		this.authAttemptEventRepository.save(user.id(), phoneNumber, AuthEventType.REGISTER, true, null, metadata);
		return new RegisterResponse(user.id(), phoneNumber, user.status().name(), otp.expiresAt());
	}

	@Transactional
	public OtpVerifyResponse verifyOtp(OtpVerifyRequest request, RequestMetadata metadata) {
		String phoneNumber = this.phoneNumberNormalizer.normalize(request.phoneNumber());
		OtpPurpose purpose = request.purpose() == null ? OtpPurpose.REGISTRATION : request.purpose();
		var otp = this.otpService.verifyOtp(phoneNumber, purpose, request.code(), metadata);
		UserRecord user = this.userRepository.findById(otp.userId())
				.orElseThrow(() -> new AuthException(AuthErrorCode.OTP_INVALID));
		if (purpose == OtpPurpose.REGISTRATION && user.status() == UserStatus.PENDING_PHONE_VERIFICATION) {
			user = this.userRepository.activatePhone(user.id());
			this.walletProvisioningRepository.createActiveWalletIfMissing(user.id());
		}
		return new OtpVerifyResponse(user.id(), user.phoneNumber(), user.status().name());
	}

	@Transactional
	public OtpResendResponse resendOtp(OtpResendRequest request, RequestMetadata metadata) {
		String phoneNumber = this.phoneNumberNormalizer.normalize(request.phoneNumber());
		OtpPurpose purpose = request.purpose() == null ? OtpPurpose.REGISTRATION : request.purpose();
		UserRecord user = this.userRepository.findByPhoneNumber(phoneNumber)
				.orElseThrow(() -> new AuthException(AuthErrorCode.OTP_INVALID));
		var otp = this.otpService.issueOtp(user.id(), phoneNumber, purpose, metadata);
		return new OtpResendResponse(phoneNumber, purpose.name(), otp.expiresAt());
	}

	@Transactional
	public AuthTokenResponse login(LoginRequest request, RequestMetadata metadata) {
		String phoneNumber = this.phoneNumberNormalizer.normalize(request.phoneNumber());
		UserRecord user = this.userRepository.findByPhoneNumber(phoneNumber)
				.filter(candidate -> candidate.status() == UserStatus.ACTIVE)
				.filter(candidate -> this.passwordEncoder.matches(request.password(), candidate.passwordHash()))
				.orElseThrow(() -> {
					this.authAttemptEventRepository.save(null, phoneNumber, AuthEventType.LOGIN, false,
							AuthErrorCode.AUTH_INVALID.name(), metadata);
					return new AuthException(AuthErrorCode.AUTH_INVALID);
		});

		// Backfill wallet for ACTIVE accounts created before CORE-WALLET-001 (account-scoped, not profile).
		this.walletProvisioningRepository.createActiveWalletIfMissing(user.id());

		UUID sessionId = UUID.randomUUID();
		TokenContext tokenContext = tokenContext(user.id());
		TokenService.AccessToken accessToken = this.tokenService.generateAccessToken(user, sessionId, tokenContext.roles(),
				tokenContext.jwtProfiles(), tokenContext.activeProfileId(), tokenContext.activeProfileType(),
				tokenContext.permissions());
		TokenService.RefreshToken refreshToken = this.tokenService.generateRefreshToken();
		this.sessionRepository.save(sessionId, user.id(), accessToken.hash(), refreshToken.hash(), request.deviceId(),
				metadata == null ? null : metadata.ipAddress(), refreshToken.expiresAt());
		this.authAttemptEventRepository.save(user.id(), phoneNumber, AuthEventType.LOGIN, true, null, metadata);
		return tokenResponse(accessToken, refreshToken, sessionId, user, tokenContext);
	}

	@Transactional
	public AuthTokenResponse refresh(RefreshRequest request, RequestMetadata metadata) {
		String refreshTokenHash = this.hasher.hash(request.refreshToken());
		var session = this.sessionRepository.findActiveByRefreshTokenHash(refreshTokenHash)
				.orElseThrow(() -> {
					this.authAttemptEventRepository.save(null, null, AuthEventType.REFRESH, false,
							AuthErrorCode.SESSION_EXPIRED.name(), metadata);
					return new AuthException(AuthErrorCode.SESSION_EXPIRED);
				});
		UserRecord user = this.userRepository.findById(session.userId())
				.filter(candidate -> candidate.status() == UserStatus.ACTIVE)
				.orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_EXPIRED));
		TokenContext tokenContext = tokenContext(user.id());
		TokenService.AccessToken accessToken = this.tokenService.generateAccessToken(user, session.id(), tokenContext.roles(),
				tokenContext.jwtProfiles(), tokenContext.activeProfileId(), tokenContext.activeProfileType(),
				tokenContext.permissions());
		TokenService.RefreshToken refreshToken = this.tokenService.generateRefreshToken();
		this.sessionRepository.rotate(session.id(), accessToken.hash(), refreshToken.hash(), request.deviceId(),
				metadata == null ? null : metadata.ipAddress(), refreshToken.expiresAt());
		this.authAttemptEventRepository.save(user.id(), user.phoneNumber(), AuthEventType.REFRESH, true, null, metadata);
		return tokenResponse(accessToken, refreshToken, session.id(), user, tokenContext);
	}

	@Transactional
	public void logout(Jwt jwt, RequestMetadata metadata) {
		UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
		UUID userId = UUID.fromString(jwt.getSubject());
		this.sessionRepository.revoke(sessionId);
		this.authAttemptEventRepository.save(userId, jwt.getClaimAsString("phone"), AuthEventType.LOGOUT, true, null, metadata);
	}

	@Transactional
	public void forgotPassword(String rawPhoneNumber, RequestMetadata metadata) {
		String phoneNumber = this.phoneNumberNormalizer.normalize(rawPhoneNumber);
		this.userRepository.findByPhoneNumber(phoneNumber)
				.filter(user -> user.status() == UserStatus.ACTIVE)
				.ifPresent(user -> this.otpService.issueOtp(user.id(), phoneNumber, OtpPurpose.PASSWORD_RESET, metadata));
		this.authAttemptEventRepository.save(null, phoneNumber, AuthEventType.PASSWORD_FORGOT, true, null, metadata);
	}

	@Transactional
	public void resetPassword(ResetPasswordRequest request, RequestMetadata metadata) {
		String phoneNumber = this.phoneNumberNormalizer.normalize(request.phoneNumber());
		UserRecord user = this.userRepository.findByPhoneNumber(phoneNumber)
				.filter(candidate -> candidate.status() == UserStatus.ACTIVE)
				.orElseThrow(() -> new AuthException(AuthErrorCode.PASSWORD_RESET_INVALID));
		try {
			this.otpService.verifyOtp(phoneNumber, OtpPurpose.PASSWORD_RESET, request.code(), metadata);
		}
		catch (AuthException ex) {
			this.authAttemptEventRepository.save(user.id(), phoneNumber, AuthEventType.PASSWORD_RESET, false,
					AuthErrorCode.PASSWORD_RESET_INVALID.name(), metadata);
			throw new AuthException(AuthErrorCode.PASSWORD_RESET_INVALID);
		}
		this.userRepository.updatePassword(user.id(), this.passwordEncoder.encode(request.newPassword()));
		this.sessionRepository.revokeAllForUser(user.id());
		this.authAttemptEventRepository.save(user.id(), phoneNumber, AuthEventType.PASSWORD_RESET, true, null, metadata);
	}

	private TokenContext tokenContext(UUID userId) {
		List<ProfileRecord> profiles = this.profileRepository.findByUserId(userId);
		Optional<ProfileRecord> activeProfile = profiles.stream().filter(ProfileRecord::active).findFirst();
		List<String> roles = profiles.stream()
				.map(profile -> profile.type().name())
				.distinct()
				.toList();
		List<ProfileTokenResponse> responseProfiles = profiles.stream()
				.map(profile -> new ProfileTokenResponse(profile.id(), profile.type().name(),
						profile.validationStatus().name(), profile.active()))
				.toList();
		List<Map<String, Object>> jwtProfiles = responseProfiles.stream()
				.map(profile -> Map.<String, Object>of(
						"id", profile.id().toString(),
						"type", profile.type(),
						"validationStatus", profile.validationStatus(),
						"active", profile.active()))
				.toList();
		List<String> permissions = activeProfile
				.map(this.permissionService::allowedActions)
				.orElseGet(List::of);
		return new TokenContext(
				roles,
				responseProfiles,
				jwtProfiles,
				activeProfile.map(ProfileRecord::id).orElse(null),
				activeProfile.map(profile -> profile.type().name()).orElse(null),
				permissions);
	}

	private static AuthTokenResponse tokenResponse(TokenService.AccessToken accessToken, TokenService.RefreshToken refreshToken,
			UUID sessionId, UserRecord user, TokenContext tokenContext) {
		return new AuthTokenResponse(
				"Bearer",
				accessToken.value(),
				refreshToken.value(),
				accessToken.expiresAt(),
				refreshToken.expiresAt(),
				sessionId,
				user.id(),
				user.phoneNumber(),
				tokenContext.roles(),
				tokenContext.responseProfiles(),
				tokenContext.activeProfileId(),
				tokenContext.activeProfileType(),
				tokenContext.permissions());
	}

	private record TokenContext(
			List<String> roles,
			List<ProfileTokenResponse> responseProfiles,
			List<Map<String, Object>> jwtProfiles,
			UUID activeProfileId,
			String activeProfileType,
			List<String> permissions) {
	}
}
