package com.motodigo.core.auth.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "motodigo.otp")
public record OtpProperties(
		@NotBlank
		String provider,
		@Min(4) @Max(10)
		int length,
		@Min(1)
		long ttlSeconds,
		@Min(1)
		int maxAttempts,
		@Min(0)
		long resendCooldownSeconds,
		@Min(1)
		int dailyLimitPerPhone,
		@Min(1)
		int dailyLimitPerIp,
		@NotBlank @Size(min = 16)
		String pepper) {

	public OtpProperties {
		if (provider == null || provider.isBlank()) {
			provider = "log";
		}
		if (length < 4 || length > 10) {
			length = 6;
		}
		if (ttlSeconds <= 0) {
			ttlSeconds = 300;
		}
		if (maxAttempts <= 0) {
			maxAttempts = 5;
		}
		if (resendCooldownSeconds < 0) {
			resendCooldownSeconds = 60;
		}
		if (dailyLimitPerPhone <= 0) {
			dailyLimitPerPhone = 10;
		}
		if (dailyLimitPerIp <= 0) {
			dailyLimitPerIp = 50;
		}
	}
}
