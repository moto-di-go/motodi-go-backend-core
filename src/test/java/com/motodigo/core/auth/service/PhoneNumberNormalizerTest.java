package com.motodigo.core.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import org.junit.jupiter.api.Test;

class PhoneNumberNormalizerTest {

	private final PhoneNumberNormalizer normalizer = new PhoneNumberNormalizer();

	@Test
	void acceptsCameroonMobileFormats() {
		assertThat(this.normalizer.normalize("+237699001122")).isEqualTo("+237699001122");
		assertThat(this.normalizer.normalize("237699001122")).isEqualTo("+237699001122");
		assertThat(this.normalizer.normalize("699001122")).isEqualTo("+237699001122");
		assertThat(this.normalizer.normalize("+237 699-001-122")).isEqualTo("+237699001122");
	}

	@Test
	void rejectsNonCameroonMobileNumbers() {
		assertThatThrownBy(() -> this.normalizer.normalize("+237599001122"))
				.isInstanceOf(AuthException.class)
				.extracting(ex -> ((AuthException) ex).errorCode())
				.isEqualTo(AuthErrorCode.INVALID_PHONE_NUMBER);
	}
}
