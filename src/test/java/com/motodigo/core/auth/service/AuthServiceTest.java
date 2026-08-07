package com.motodigo.core.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.auth.dto.LoginRequest;
import com.motodigo.core.auth.dto.OtpVerifyRequest;
import com.motodigo.core.auth.dto.RefreshRequest;
import com.motodigo.core.auth.entity.AuthEventType;
import com.motodigo.core.auth.entity.OtpPurpose;
import com.motodigo.core.auth.entity.OtpRecord;
import com.motodigo.core.auth.entity.UserRecord;
import com.motodigo.core.auth.entity.UserStatus;
import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.auth.repository.AuthAttemptEventRepository;
import com.motodigo.core.auth.repository.SessionRepository;
import com.motodigo.core.auth.repository.UserRepository;
import com.motodigo.core.profile.repository.ProfileRepository;
import com.motodigo.core.profile.service.PermissionService;
import com.motodigo.core.wallet.WalletProvisioningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final RequestMetadata METADATA = new RequestMetadata("127.0.0.1", "JUnit", "corr-test");

	@Mock
	private UserRepository userRepository;

	@Mock
	private SessionRepository sessionRepository;

	@Mock
	private WalletProvisioningRepository walletProvisioningRepository;

	@Mock
	private AuthAttemptEventRepository authAttemptEventRepository;

	@Mock
	private ProfileRepository profileRepository;

	@Mock
	private PermissionService permissionService;

	@Mock
	private OtpService otpService;

	@Mock
	private TokenService tokenService;

	private AuthService authService;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@BeforeEach
	void setUp() {
		this.authService = new AuthService(
				this.userRepository,
				this.sessionRepository,
				this.walletProvisioningRepository,
				this.authAttemptEventRepository,
				this.profileRepository,
				this.permissionService,
				this.otpService,
				this.tokenService,
				new PhoneNumberNormalizer(),
				new Sha256TokenHasher(),
				this.passwordEncoder);
	}

	@Test
	void forgotPasswordDoesNotExposeUnknownAccount() {
		when(this.userRepository.findByPhoneNumber("+237699001122")).thenReturn(Optional.empty());

		this.authService.forgotPassword("699001122", METADATA);

		verify(this.otpService, never()).issueOtp(any(), any(), any(), any());
		verify(this.authAttemptEventRepository).save(
				eq(null),
				eq("+237699001122"),
				eq(AuthEventType.PASSWORD_FORGOT),
				eq(true),
				eq(null),
				eq(METADATA));
	}

	@Test
	void refreshUnknownTokenReturnsSessionExpired() {
		when(this.sessionRepository.findActiveByRefreshTokenHash(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.authService.refresh(new RefreshRequest("missing-token", "device"), METADATA))
				.isInstanceOf(AuthException.class)
				.extracting(ex -> ((AuthException) ex).errorCode())
				.isEqualTo(AuthErrorCode.SESSION_EXPIRED);
	}

	@Test
	void logoutRevokesSessionFromJwtSid() {
		UUID sessionId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.subject(userId.toString())
				.claim("sid", sessionId.toString())
				.claim("phone", "+237699001122")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60))
				.build();

		this.authService.logout(jwt, METADATA);

		verify(this.sessionRepository).revoke(sessionId);
		verify(this.authAttemptEventRepository).save(userId, "+237699001122", AuthEventType.LOGOUT, true, null, METADATA);
	}

	@Test
	void verifyRegistrationOtpActivatesAccountAndProvisionsWallet() {
		UUID userId = UUID.randomUUID();
		UserRecord pending = pendingUser(userId);
		UserRecord active = activeUser(userId);
		when(this.otpService.verifyOtp(eq("+237699001122"), eq(OtpPurpose.REGISTRATION), eq("123456"), eq(METADATA)))
				.thenReturn(otpRecord(userId));
		when(this.userRepository.findById(userId)).thenReturn(Optional.of(pending));
		when(this.userRepository.activatePhone(userId)).thenReturn(active);

		this.authService.verifyOtp(new OtpVerifyRequest("699001122", "123456", OtpPurpose.REGISTRATION), METADATA);

		verify(this.userRepository).activatePhone(userId);
		verify(this.walletProvisioningRepository).createActiveWalletIfMissing(userId);
	}

	@Test
	void loginEnsuresWalletForActiveAccount() {
		UUID userId = UUID.randomUUID();
		String password = "Secret1234";
		UserRecord user = new UserRecord(
				userId,
				"+237699001122",
				this.passwordEncoder.encode(password),
				"Test User",
				null,
				"fr-CM",
				true,
				UserStatus.ACTIVE,
				Instant.now(),
				Instant.now(),
				Instant.now());
		when(this.userRepository.findByPhoneNumber("+237699001122")).thenReturn(Optional.of(user));
		when(this.profileRepository.findByUserId(userId)).thenReturn(List.of());
		when(this.tokenService.generateAccessToken(eq(user), any(), anyList(), anyList(), isNull(), isNull(), anyList()))
				.thenReturn(new TokenService.AccessToken("access", Instant.now().plusSeconds(60), "access-hash"));
		when(this.tokenService.generateRefreshToken())
				.thenReturn(new TokenService.RefreshToken("refresh", Instant.now().plusSeconds(3600), "refresh-hash"));

		this.authService.login(new LoginRequest("699001122", password, "device-1"), METADATA);

		verify(this.walletProvisioningRepository).createActiveWalletIfMissing(userId);
	}

	private static OtpRecord otpRecord(UUID userId) {
		return new OtpRecord(
				UUID.randomUUID(),
				userId,
				"+237699001122",
				OtpPurpose.REGISTRATION,
				"hash",
				0,
				Instant.now().plusSeconds(300),
				null,
				Instant.now());
	}

	private static UserRecord pendingUser(UUID userId) {
		return new UserRecord(
				userId,
				"+237699001122",
				"hash",
				"Test User",
				null,
				"fr-CM",
				true,
				UserStatus.PENDING_PHONE_VERIFICATION,
				null,
				Instant.now(),
				Instant.now());
	}

	private static UserRecord activeUser(UUID userId) {
		return new UserRecord(
				userId,
				"+237699001122",
				"hash",
				"Test User",
				null,
				"fr-CM",
				true,
				UserStatus.ACTIVE,
				Instant.now(),
				Instant.now(),
				Instant.now());
	}
}
