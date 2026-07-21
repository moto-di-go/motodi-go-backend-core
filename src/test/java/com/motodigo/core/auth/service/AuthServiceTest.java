package com.motodigo.core.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.auth.dto.RefreshRequest;
import com.motodigo.core.auth.entity.AuthEventType;
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
				new BCryptPasswordEncoder());
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

	@SuppressWarnings("unused")
	private UserRecord activeUser(UUID userId) {
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
