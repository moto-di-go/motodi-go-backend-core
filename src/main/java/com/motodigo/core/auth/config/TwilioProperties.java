package com.motodigo.core.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "motodigo.twilio")
public record TwilioProperties(
		String accountSid,
		String authToken,
		String apiKeySid,
		String apiKeySecret,
		String fromNumber,
		int connectTimeoutMs,
		int readTimeoutMs) {

	public TwilioProperties {
		accountSid = accountSid == null ? "" : accountSid.strip();
		authToken = authToken == null ? "" : authToken.strip();
		apiKeySid = apiKeySid == null ? "" : apiKeySid.strip();
		apiKeySecret = apiKeySecret == null ? "" : apiKeySecret.strip();
		fromNumber = fromNumber == null ? "" : fromNumber.strip();
		if (connectTimeoutMs <= 0) {
			connectTimeoutMs = 5000;
		}
		if (readTimeoutMs <= 0) {
			readTimeoutMs = 10000;
		}
	}
}
