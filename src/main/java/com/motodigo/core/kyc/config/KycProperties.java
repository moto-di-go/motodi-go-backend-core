package com.motodigo.core.kyc.config;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "motodigo.kyc")
public record KycProperties(
		@Valid Storage storage,
		@Valid Encryption encryption,
		String checksumPepper,
		long maxFileSizeBytes) {

	private static final String DEFAULT_CHECKSUM_PEPPER = "change-me-kyc-checksum-pepper";
	private static final String DEFAULT_LOCAL_KEY = "bW90b2RpZ28tbG9jYWwta3ljLWtleS0zMi1ieXRlISE=";
	private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;

	public KycProperties {
		if (encryption == null) {
			encryption = new Encryption("local", Map.of("local", DEFAULT_LOCAL_KEY));
		}
		if (checksumPepper == null || checksumPepper.isBlank()) {
			checksumPepper = DEFAULT_CHECKSUM_PEPPER;
		}
		if (maxFileSizeBytes <= 0) {
			maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
		}
	}

	public record Storage(
			@NotBlank String endpoint,
			@NotBlank String region,
			@NotBlank String bucket,
			@NotBlank String accessKey,
			@NotBlank String secretKey,
			boolean pathStyle) {
	}

	public record Encryption(
			@NotBlank String activeKeyId,
			Map<String, String> keys) {

		public Encryption {
			keys = keys == null ? Map.of() : Map.copyOf(keys);
		}
	}
}
