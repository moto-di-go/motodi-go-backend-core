package com.motodigo.core.kyc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import com.motodigo.core.kyc.config.KycProperties;
import com.motodigo.core.kyc.exception.KycException;
import org.junit.jupiter.api.Test;

class KycCryptoServiceTest {

	@Test
	void encryptDecryptRoundTripAndDoesNotStorePlaintext() {
		KycCryptoService service = new KycCryptoService(properties(key((byte) 7), "pepper"));
		byte[] plaintext = "%PDF-1.7 sensitive document".getBytes(StandardCharsets.UTF_8);

		byte[] encrypted = service.encrypt(plaintext);

		assertThat(encrypted).isNotEqualTo(plaintext);
		assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain("sensitive document");
		assertThat(service.decrypt(encrypted)).isEqualTo(plaintext);
	}

	@Test
	void decryptRejectsWrongKeyAndTamperedPayload() {
		KycCryptoService service = new KycCryptoService(properties(key((byte) 7), "pepper"));
		byte[] encrypted = service.encrypt("secret".getBytes(StandardCharsets.UTF_8));

		KycCryptoService wrongKey = new KycCryptoService(properties(key((byte) 8), "pepper"));
		assertThatThrownBy(() -> wrongKey.decrypt(encrypted)).isInstanceOf(KycException.class);

		encrypted[encrypted.length - 1] = (byte) (encrypted[encrypted.length - 1] ^ 1);
		assertThatThrownBy(() -> service.decrypt(encrypted)).isInstanceOf(KycException.class);
	}

	@Test
	void checksumUsesPepper() {
		byte[] plaintext = "same document".getBytes(StandardCharsets.UTF_8);
		String first = new KycCryptoService(properties(key((byte) 7), "pepper-a")).checksum(plaintext);
		String second = new KycCryptoService(properties(key((byte) 7), "pepper-b")).checksum(plaintext);

		assertThat(first).isNotEqualTo(second);
	}

	private static KycProperties properties(String key, String pepper) {
		return new KycProperties(
				new KycProperties.Storage("http://localhost:9000", "us-east-1", "bucket", "access", "secret", true),
				new KycProperties.Encryption("local", Map.of("local", key)),
				pepper,
				5_242_880);
	}

	private static String key(byte value) {
		byte[] bytes = new byte[32];
		java.util.Arrays.fill(bytes, value);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
