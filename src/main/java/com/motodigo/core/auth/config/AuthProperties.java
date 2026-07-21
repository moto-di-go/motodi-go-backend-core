package com.motodigo.core.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "motodigo.auth")
public record AuthProperties(
		@NotBlank @Size(min = 32)
		String jwtSecret,
		@NotBlank
		String issuerUrl,
		@NotBlank
		String audience,
		@Min(1)
		long accessTtlSeconds,
		@Min(1)
		long refreshTtlSeconds) {

	public AuthProperties {
		if (issuerUrl == null || issuerUrl.isBlank()) {
			issuerUrl = "http://localhost:8081";
		}
		if (audience == null || audience.isBlank()) {
			audience = "motodi-go-api";
		}
		if (accessTtlSeconds <= 0) {
			accessTtlSeconds = 900;
		}
		if (refreshTtlSeconds <= 0) {
			refreshTtlSeconds = 2_592_000;
		}
	}
}
