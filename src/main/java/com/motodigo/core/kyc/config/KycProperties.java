package com.motodigo.core.kyc.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "motodigo.kyc")
public record KycProperties(
		@Valid Storage storage) {

	public record Storage(
			@NotBlank String endpoint,
			@NotBlank String region,
			@NotBlank String bucket,
			@NotBlank String accessKey,
			@NotBlank String secretKey,
			boolean pathStyle) {
	}
}
