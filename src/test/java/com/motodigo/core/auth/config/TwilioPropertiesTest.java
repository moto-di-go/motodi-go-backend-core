package com.motodigo.core.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TwilioPropertiesTest {

	@Test
	void stripsWhitespaceFromFields() {
		TwilioProperties props = new TwilioProperties(" AC123 ", "", " SK123 ", " secret ", " +1555 ", 5000, 10000);

		assertThat(props.accountSid()).isEqualTo("AC123");
		assertThat(props.apiKeySid()).isEqualTo("SK123");
		assertThat(props.apiKeySecret()).isEqualTo("secret");
		assertThat(props.fromNumber()).isEqualTo("+1555");
	}

	@Test
	void defaultsNullFieldsToEmpty() {
		TwilioProperties props = new TwilioProperties(null, null, null, null, null, 0, 0);

		assertThat(props.accountSid()).isEmpty();
		assertThat(props.apiKeySid()).isEmpty();
		assertThat(props.apiKeySecret()).isEmpty();
		assertThat(props.fromNumber()).isEmpty();
	}

	@Test
	void defaultsTimeoutsWhenZeroOrNegative() {
		TwilioProperties props = new TwilioProperties("", "", "", "", "", 0, -1);

		assertThat(props.connectTimeoutMs()).isEqualTo(5000);
		assertThat(props.readTimeoutMs()).isEqualTo(10000);
	}
}
