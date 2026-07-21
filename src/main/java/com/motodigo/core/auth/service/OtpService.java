package com.motodigo.core.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.auth.config.OtpProperties;
import com.motodigo.core.auth.entity.AuthEventType;
import com.motodigo.core.auth.entity.OtpPurpose;
import com.motodigo.core.auth.entity.OtpRecord;
import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.auth.repository.AuthAttemptEventRepository;
import com.motodigo.core.auth.repository.OtpRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class OtpService {

	private final OtpProperties properties;
	private final OtpRepository otpRepository;
	private final AuthAttemptEventRepository authAttemptEventRepository;
	private final OtpSender otpSender;
	private final Sha256TokenHasher hasher;
	private final SecureRandom secureRandom = new SecureRandom();

	public OtpService(OtpProperties properties, OtpRepository otpRepository,
			AuthAttemptEventRepository authAttemptEventRepository, OtpSender otpSender, Sha256TokenHasher hasher) {
		this.properties = properties;
		this.otpRepository = otpRepository;
		this.authAttemptEventRepository = authAttemptEventRepository;
		this.otpSender = otpSender;
		this.hasher = hasher;
	}

	public OtpRecord issueOtp(UUID userId, String phoneNumber, OtpPurpose purpose, RequestMetadata metadata) {
		enforceRateLimits(phoneNumber, purpose, metadata);

		String code = generateCode();
		Instant expiresAt = Instant.now().plusSeconds(this.properties.ttlSeconds());
		OtpRecord otp = this.otpRepository.save(userId, phoneNumber, purpose, hashOtp(phoneNumber, purpose, code), expiresAt);
		this.otpSender.sendOtp(phoneNumber, buildMessage(code, purpose));
		this.authAttemptEventRepository.save(userId, phoneNumber, AuthEventType.OTP_SEND, true, null, metadata);
		return otp;
	}

	public OtpRecord verifyOtp(String phoneNumber, OtpPurpose purpose, String rawCode, RequestMetadata metadata) {
		OtpRecord otp = this.otpRepository.findLatestActive(phoneNumber, purpose)
				.orElseThrow(() -> {
					this.authAttemptEventRepository.save(null, phoneNumber, AuthEventType.OTP_VERIFY, false,
							AuthErrorCode.OTP_INVALID.name(), metadata);
					return new AuthException(AuthErrorCode.OTP_INVALID);
				});

		if (otp.attempts() >= this.properties.maxAttempts()) {
			this.authAttemptEventRepository.save(otp.userId(), phoneNumber, AuthEventType.OTP_VERIFY, false,
					AuthErrorCode.OTP_INVALID.name(), metadata);
			throw new AuthException(AuthErrorCode.OTP_INVALID);
		}

		String expectedHash = hashOtp(phoneNumber, purpose, rawCode);
		if (!expectedHash.equals(otp.codeHash())) {
			this.otpRepository.incrementAttempts(otp.id());
			this.authAttemptEventRepository.save(otp.userId(), phoneNumber, AuthEventType.OTP_VERIFY, false,
					AuthErrorCode.OTP_INVALID.name(), metadata);
			throw new AuthException(AuthErrorCode.OTP_INVALID);
		}

		if (!this.otpRepository.consumeIfActive(otp.id(), this.properties.maxAttempts())) {
			this.authAttemptEventRepository.save(otp.userId(), phoneNumber, AuthEventType.OTP_VERIFY, false,
					AuthErrorCode.OTP_INVALID.name(), metadata);
			throw new AuthException(AuthErrorCode.OTP_INVALID);
		}
		this.authAttemptEventRepository.save(otp.userId(), phoneNumber, AuthEventType.OTP_VERIFY, true, null, metadata);
		return otp;
	}

	public String hashOtp(String phoneNumber, OtpPurpose purpose, String rawCode) {
		return this.hasher.hash(phoneNumber + ":" + purpose.name() + ":" + rawCode + ":" + this.properties.pepper());
	}

	private void enforceRateLimits(String phoneNumber, OtpPurpose purpose, RequestMetadata metadata) {
		Instant oneDayAgo = Instant.now().minus(Duration.ofDays(1));
		if (this.otpRepository.countCreatedSince(phoneNumber, purpose, oneDayAgo) >= this.properties.dailyLimitPerPhone()) {
			this.authAttemptEventRepository.save(null, phoneNumber, AuthEventType.OTP_SEND, false,
					AuthErrorCode.OTP_RATE_LIMITED.name(), metadata);
			throw new AuthException(AuthErrorCode.OTP_RATE_LIMITED);
		}
		if (metadata != null
				&& this.authAttemptEventRepository.countOtpSendsByIpSince(metadata.ipAddress(), oneDayAgo) >= this.properties.dailyLimitPerIp()) {
			this.authAttemptEventRepository.save(null, phoneNumber, AuthEventType.OTP_SEND, false,
					AuthErrorCode.OTP_RATE_LIMITED.name(), metadata);
			throw new AuthException(AuthErrorCode.OTP_RATE_LIMITED);
		}
		this.otpRepository.findLatest(phoneNumber, purpose)
				.filter(otp -> otp.createdAt().isAfter(Instant.now().minusSeconds(this.properties.resendCooldownSeconds()))
						&& otp.consumedAt() == null)
				.ifPresent(otp -> {
					this.authAttemptEventRepository.save(otp.userId(), phoneNumber, AuthEventType.OTP_SEND, false,
							AuthErrorCode.OTP_RATE_LIMITED.name(), metadata);
					throw new AuthException(AuthErrorCode.OTP_RATE_LIMITED);
				});
	}

	private String generateCode() {
		StringBuilder code = new StringBuilder(this.properties.length());
		for (int i = 0; i < this.properties.length(); i++) {
			code.append(this.secureRandom.nextInt(10));
		}
		return code.toString();
	}

	private static String buildMessage(String code, OtpPurpose purpose) {
		return "MOTO'DI GO: votre code " + purpose.name().toLowerCase().replace('_', ' ') + " est " + code
				+ ". Il expire dans quelques minutes.";
	}
}
