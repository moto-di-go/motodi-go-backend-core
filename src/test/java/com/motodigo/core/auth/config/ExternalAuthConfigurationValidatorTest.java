package com.motodigo.core.auth.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ExternalAuthConfigurationValidatorTest {

	@Test
	void allowsLogProviderOnLocalProfile() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("local");
		ExternalAuthConfigurationValidator validator = validator(environment, "log", "", "", "");

		assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
	}

	@Test
	void rejectsLogProviderOnStaging() {
		ExternalAuthConfigurationValidator validator = validator(stagingEnvironment(), "log", "user", "key", "SENDER");

		assertThatThrownBy(validator::afterSingletonsInstantiated)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("OTP_PROVIDER=log");
	}

	@Test
	void rejectsMissingAfricasTalkingCredentialsOnStaging() {
		ExternalAuthConfigurationValidator validator = validator(stagingEnvironment(), "africas-talking", "", "", "");

		assertThatThrownBy(validator::afterSingletonsInstantiated)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AFRICAS_TALKING_USERNAME");
	}

	@Test
	void acceptsAfricasTalkingOnStagingWhenCredentialsArePresent() {
		ExternalAuthConfigurationValidator validator = validator(stagingEnvironment(), "africas-talking",
				"staging-user", "staging-key", "MOTODIGO");

		assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
	}

	private static MockEnvironment stagingEnvironment() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("staging");
		return environment;
	}

	private static ExternalAuthConfigurationValidator validator(MockEnvironment environment, String provider,
			String username, String apiKey, String senderId) {
		AuthProperties authProperties = new AuthProperties(
				"staging-jwt-secret-at-least-32-bytes-long",
				"https://staging.example.com",
				"motodi-go-api",
				900,
				2592000);
		OtpProperties otpProperties = new OtpProperties(provider, 6, 300, 5, 60, 10, 50,
				"staging-otp-pepper-16");
		AfricasTalkingProperties africasTalkingProperties = new AfricasTalkingProperties(
				URI.create("https://api.africastalking.com"),
				username,
				apiKey,
				senderId,
				1,
				3000,
				5000);
		return new ExternalAuthConfigurationValidator(environment, authProperties, otpProperties,
				africasTalkingProperties);
	}
}
