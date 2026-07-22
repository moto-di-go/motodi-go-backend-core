package com.motodigo.core.kyc.config;

import java.net.URI;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "motodigo.kyc")
public record KycProperties(
		@Valid Storage storage,
		@Valid Encryption encryption,
		@NotBlank String checksumPepper,
		@Min(1) long maxFileSizeBytes) {

	public record Storage(
			@NotBlank String endpoint,
			@NotBlank String region,
			@NotBlank String bucket,
			@NotBlank String accessKey,
			@NotBlank String secretKey,
			boolean pathStyle) {

		public URI endpointUri() {
			return URI.create(this.endpoint);
		}
	}

	public record Encryption(
			@NotBlank String activeKeyId,
			@NotEmpty Map<String, String> keys) {
	}
}
