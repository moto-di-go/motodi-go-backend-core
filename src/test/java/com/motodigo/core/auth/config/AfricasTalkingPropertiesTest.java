package com.motodigo.core.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

class AfricasTalkingPropertiesTest {

	@Test
	void stripsBulkMessagingPathFromConfiguredBaseUrl() {
		AfricasTalkingProperties properties = properties(
				URI.create("https://api.africastalking.com/version1/messaging/bulk"));

		assertThat(properties.baseUrl()).isEqualTo(URI.create("https://api.africastalking.com"));
	}

	@Test
	void stripsBulkMessagingPathAndTrailingSlash() {
		AfricasTalkingProperties properties = properties(
				URI.create("https://api.africastalking.com/version1/messaging/bulk/"));

		assertThat(properties.baseUrl()).isEqualTo(URI.create("https://api.africastalking.com"));
	}

	@Test
	void keepsHostOnlyBaseUrl() {
		AfricasTalkingProperties properties = properties(URI.create("https://api.africastalking.com"));

		assertThat(properties.baseUrl()).isEqualTo(URI.create("https://api.africastalking.com"));
	}

	@Test
	void stripsBulkPathFromSandboxHost() {
		URI normalized = AfricasTalkingProperties.normalizeBaseUrl(
				URI.create("https://api.sandbox.africastalking.com/version1/messaging/bulk"));

		assertThat(normalized).isEqualTo(URI.create("https://api.sandbox.africastalking.com"));
	}

	@Test
	void stripsClassicMessagingPathFromBaseUrl() {
		URI normalized = AfricasTalkingProperties.normalizeBaseUrl(
				URI.create("https://api.sandbox.africastalking.com/version1/messaging"));

		assertThat(normalized).isEqualTo(URI.create("https://api.sandbox.africastalking.com"));
	}

	@Test
	void normalizesSandboxApiUsername() {
		AfricasTalkingProperties properties = new AfricasTalkingProperties(
				URI.create("https://api.sandbox.africastalking.com"),
				"Sandbox",
				"test-key",
				"Sandbox",
				true,
				3000,
				5000);

		assertThat(properties.apiUsername()).isEqualTo("sandbox");
		assertThat(properties.sandbox()).isTrue();
		assertThat(properties.requestFrom()).isEmpty();
	}

	@Test
	void defaultsNullBaseUrl() {
		assertThat(AfricasTalkingProperties.normalizeBaseUrl(null))
				.isEqualTo(URI.create("https://api.africastalking.com"));
	}

	@Test
	void rewritesLiveHostToSandboxWhenUsernameIsSandbox() {
		AfricasTalkingProperties properties = new AfricasTalkingProperties(
				URI.create("https://api.africastalking.com"),
				"Sandbox",
				"test-key",
				"Sandbox",
				true,
				3000,
				5000);

		assertThat(properties.baseUrl()).isEqualTo(URI.create("https://api.sandbox.africastalking.com"));
	}

	@Test
	void keepsExplicitSandboxHost() {
		AfricasTalkingProperties properties = new AfricasTalkingProperties(
				URI.create("https://api.sandbox.africastalking.com"),
				"Sandbox",
				"test-key",
				"Sandbox",
				true,
				3000,
				5000);

		assertThat(properties.baseUrl()).isEqualTo(URI.create("https://api.sandbox.africastalking.com"));
	}

	private static AfricasTalkingProperties properties(URI baseUrl) {
		return new AfricasTalkingProperties(baseUrl, "test-user", "test-key", "AFRICASTKNG", true, 3000, 5000);
	}
}
