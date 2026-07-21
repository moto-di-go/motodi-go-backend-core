package com.motodigo.core.auth.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
class ExternalAuthConfigurationValidator implements SmartInitializingSingleton {

	private static final Set<String> LOCAL_PROFILES = Set.of("local", "test");
	private static final String DEFAULT_JWT_SECRET = "change-me-to-at-least-32-bytes-for-hs256";
	private static final String DEFAULT_OTP_PEPPER = "change-me-otp-pepper";

	private final Environment environment;
	private final AuthProperties authProperties;
	private final OtpProperties otpProperties;
	private final AfricasTalkingProperties africasTalkingProperties;

	ExternalAuthConfigurationValidator(Environment environment, AuthProperties authProperties, OtpProperties otpProperties,
			AfricasTalkingProperties africasTalkingProperties) {
		this.environment = environment;
		this.authProperties = authProperties;
		this.otpProperties = otpProperties;
		this.africasTalkingProperties = africasTalkingProperties;
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (isLocalOrTest()) {
			return;
		}

		requireNonDefault("JWT_SECRET", this.authProperties.jwtSecret(), DEFAULT_JWT_SECRET);
		requireNonDefault("OTP_PEPPER", this.otpProperties.pepper(), DEFAULT_OTP_PEPPER);

		String provider = this.otpProperties.provider().toLowerCase(Locale.ROOT);
		if ("log".equals(provider)) {
			throw new IllegalStateException("OTP_PROVIDER=log is allowed only with local or test profiles");
		}
		if ("africas-talking".equals(provider)) {
			requireNonBlank("AFRICAS_TALKING_USERNAME", this.africasTalkingProperties.username());
			requireNonBlank("AFRICAS_TALKING_API_KEY", this.africasTalkingProperties.apiKey());
			requireNonBlank("AFRICAS_TALKING_SENDER_ID", this.africasTalkingProperties.senderId());
		}
	}

	private boolean isLocalOrTest() {
		return Arrays.stream(this.environment.getActiveProfiles()).anyMatch(LOCAL_PROFILES::contains);
	}

	private static void requireNonDefault(String name, String value, String defaultValue) {
		requireNonBlank(name, value);
		if (defaultValue.equals(value)) {
			throw new IllegalStateException(name + " must not use the local placeholder outside local/test profiles");
		}
	}

	private static void requireNonBlank(String name, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " is required outside local/test profiles");
		}
	}
}
