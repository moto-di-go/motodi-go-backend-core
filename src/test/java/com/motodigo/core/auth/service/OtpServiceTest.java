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

import com.motodigo.core.auth.config.OtpProperties;
import com.motodigo.core.auth.entity.OtpPurpose;
import com.motodigo.core.auth.entity.OtpRecord;
import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.auth.repository.AuthAttemptEventRepository;
import com.motodigo.core.auth.repository.OtpRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

	private static final RequestMetadata METADATA = new RequestMetadata("127.0.0.1", "JUnit", "corr-test");

	@Mock
	private OtpRepository otpRepository;

	@Mock
	private AuthAttemptEventRepository authAttemptEventRepository;

	@Mock
	private OtpSender otpSender;

	@Test
	void invalidOtpIncrementsAttemptsAndDoesNotConsume() {
		OtpService service = service();
		UUID otpId = UUID.randomUUID();
		when(this.otpRepository.findLatestActive("+237699001122", OtpPurpose.REGISTRATION))
				.thenReturn(Optional.of(new OtpRecord(
						otpId,
						UUID.randomUUID(),
						"+237699001122",
						OtpPurpose.REGISTRATION,
						service.hashOtp("+237699001122", OtpPurpose.REGISTRATION, "111111"),
						0,
						Instant.now().plusSeconds(300),
						null,
						Instant.now())));

		assertThatThrownBy(() -> service.verifyOtp("+237699001122", OtpPurpose.REGISTRATION, "222222", METADATA))
				.isInstanceOf(AuthException.class)
				.extracting(ex -> ((AuthException) ex).errorCode())
				.isEqualTo(AuthErrorCode.OTP_INVALID);

		verify(this.otpRepository).incrementAttempts(otpId);
		verify(this.otpRepository, never()).consumeIfActive(any(), eq(5));
	}

	@Test
	void issueOtpHonorsDailyPhoneLimit() {
		OtpService service = service();
		when(this.otpRepository.countCreatedSince(eq("+237699001122"), eq(OtpPurpose.REGISTRATION), any()))
				.thenReturn(10);

		assertThatThrownBy(() -> service.issueOtp(UUID.randomUUID(), "+237699001122", OtpPurpose.REGISTRATION, METADATA))
				.isInstanceOf(AuthException.class)
				.extracting(ex -> ((AuthException) ex).errorCode())
				.isEqualTo(AuthErrorCode.OTP_RATE_LIMITED);

		verify(this.otpSender, never()).sendOtp(any(), any());
	}

	private OtpService service() {
		return new OtpService(
				new OtpProperties("log", 6, 300, 5, 60, 10, 50, "pepper"),
				this.otpRepository,
				this.authAttemptEventRepository,
				this.otpSender,
				new Sha256TokenHasher());
	}
}
