package com.motodigo.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.auth.service.OtpSender;
import org.junit.jupiter.api.Test;

class FallbackOtpSenderTest {

	@Test
	void delegatesToPrimaryWhenSuccessful() {
		AtomicInteger primaryCalls = new AtomicInteger();
		AtomicInteger fallbackCalls = new AtomicInteger();
		FallbackOtpSender sender = fallbackSender(
				(phone, msg) -> primaryCalls.incrementAndGet(),
				(phone, msg) -> fallbackCalls.incrementAndGet());

		sender.sendOtp("+237699001122", "123456");

		assertThat(primaryCalls.get()).isEqualTo(1);
		assertThat(fallbackCalls.get()).isZero();
	}

	@Test
	void fallsBackToSecondaryWhenPrimaryFails() {
		AtomicInteger fallbackCalls = new AtomicInteger();
		FallbackOtpSender sender = fallbackSender(
				(phone, msg) -> { throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED); },
				(phone, msg) -> fallbackCalls.incrementAndGet());

		sender.sendOtp("+237699001122", "123456");

		assertThat(fallbackCalls.get()).isEqualTo(1);
	}

	@Test
	void propagatesFallbackFailure() {
		FallbackOtpSender sender = fallbackSender(
				(phone, msg) -> { throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED); },
				(phone, msg) -> { throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED); });

		assertThatThrownBy(() -> sender.sendOtp("+237699001122", "123456"))
				.isInstanceOf(AuthException.class);
	}

	private static FallbackOtpSender fallbackSender(OtpSender primary, OtpSender fallback) {
		return new FallbackOtpSender(
				new DelegatingTwilioSender(primary),
				new DelegatingAfricasTalkingSender(fallback));
	}

	private static final class DelegatingTwilioSender extends TwilioOtpSender {
		private final OtpSender delegate;

		DelegatingTwilioSender(OtpSender delegate) {
			super(new com.motodigo.core.auth.config.TwilioProperties("", "", "", "", "", 1000, 1000), null);
			this.delegate = delegate;
		}

		@Override
		public void sendOtp(String phoneNumber, String message) {
			this.delegate.sendOtp(phoneNumber, message);
		}
	}

	private static final class DelegatingAfricasTalkingSender extends AfricasTalkingOtpSender {
		private final OtpSender delegate;

		DelegatingAfricasTalkingSender(OtpSender delegate) {
			super(new com.motodigo.core.auth.config.AfricasTalkingProperties(
					java.net.URI.create("http://localhost:9"), "", "", "", true, 1000, 1000), null);
			this.delegate = delegate;
		}

		@Override
		public void sendOtp(String phoneNumber, String message) {
			this.delegate.sendOtp(phoneNumber, message);
		}
	}
}
