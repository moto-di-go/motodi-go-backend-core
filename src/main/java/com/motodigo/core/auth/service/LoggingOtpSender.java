package com.motodigo.core.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "motodigo.otp", name = "provider", havingValue = "log", matchIfMissing = true)
class LoggingOtpSender implements OtpSender {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoggingOtpSender.class);

	@Override
	public void sendOtp(String phoneNumber, String message) {
		LOGGER.info("OTP delivery accepted by local log provider for {}: {}", phoneNumber, message);
	}
}
