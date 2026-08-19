package com.motodigo.core.auth;

import com.motodigo.core.auth.config.TwilioProperties;
import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.auth.service.OtpSender;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwilioOtpSender implements OtpSender {

	private static final Logger LOGGER = LoggerFactory.getLogger(TwilioOtpSender.class);

	private final TwilioProperties properties;
	private final TwilioRestClient restClient;

	public TwilioOtpSender(TwilioProperties properties) {
		this(properties, buildClient(properties));
	}

	TwilioOtpSender(TwilioProperties properties, TwilioRestClient restClient) {
		this.properties = properties;
		this.restClient = restClient;
	}

	@Override
	public void sendOtp(String phoneNumber, String message) {
		if (this.properties.accountSid().isBlank() || this.properties.fromNumber().isBlank()
				|| !hasApiKeyAuth(this.properties) && !hasAuthTokenAuth(this.properties)) {
			LOGGER.warn("Twilio OTP delivery failed: missing credentials or from number");
			throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED);
		}

		try {
			Message sms = Message.creator(
					new PhoneNumber(phoneNumber),
					new PhoneNumber(this.properties.fromNumber()),
					message)
					.create(this.restClient);

			String status = sms.getStatus() == null ? "unknown" : sms.getStatus().toString();
			if ("failed".equalsIgnoreCase(status) || "undelivered".equalsIgnoreCase(status)) {
				LOGGER.warn("Twilio OTP delivery rejected: sid={}, status={}, errorCode={}",
						sms.getSid(), status, sms.getErrorCode());
				throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED);
			}
			LOGGER.info("Twilio OTP accepted: sid={}, status={}", sms.getSid(), status);
		}
		catch (AuthException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			LOGGER.warn("Twilio OTP request failed: {}", ex.getMessage());
			throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED);
		}
	}

	private static boolean hasApiKeyAuth(TwilioProperties properties) {
		return !properties.apiKeySid().isBlank() && !properties.apiKeySecret().isBlank();
	}

	private static boolean hasAuthTokenAuth(TwilioProperties properties) {
		return !properties.authToken().isBlank();
	}

	private static TwilioRestClient buildClient(TwilioProperties properties) {
		if (hasApiKeyAuth(properties)) {
			return new TwilioRestClient.Builder(properties.apiKeySid(), properties.apiKeySecret())
					.accountSid(properties.accountSid())
					.build();
		}
		return new TwilioRestClient.Builder(properties.accountSid(), properties.authToken())
				.build();
	}
}
