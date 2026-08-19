package com.motodigo.core.auth;

import com.motodigo.core.auth.config.AfricasTalkingProperties;
import com.motodigo.core.auth.config.TwilioProperties;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.auth.service.OtpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "motodigo.otp", name = "provider", havingValue = "twilio")
public class FallbackOtpSender implements OtpSender {

	private static final Logger LOGGER = LoggerFactory.getLogger(FallbackOtpSender.class);

	private final TwilioOtpSender primary;
	private final AfricasTalkingOtpSender fallback;

	@Autowired
	public FallbackOtpSender(TwilioProperties twilioProperties, AfricasTalkingProperties africasTalkingProperties) {
		this.primary = new TwilioOtpSender(twilioProperties);
		this.fallback = new AfricasTalkingOtpSender(africasTalkingProperties);
	}

	FallbackOtpSender(TwilioOtpSender primary, AfricasTalkingOtpSender fallback) {
		this.primary = primary;
		this.fallback = fallback;
	}

	@Override
	public void sendOtp(String phoneNumber, String message) {
		try {
			this.primary.sendOtp(phoneNumber, message);
		}
		catch (AuthException ex) {
			LOGGER.warn("Twilio failed, falling back to Africa's Talking: {}", ex.getMessage());
			this.fallback.sendOtp(phoneNumber, message);
		}
	}
}
