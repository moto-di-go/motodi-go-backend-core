package com.motodigo.core.offline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "motodigo.offline")
public record OfflineProperties(
		@NotBlank String mapBaseUrl,
		@Positive int mapPackTtlDays) {

	public OfflineProperties {
		if (mapBaseUrl == null || mapBaseUrl.isBlank()) {
			mapBaseUrl = "http://localhost:9000/motodigo-maps";
		}
		if (mapPackTtlDays <= 0) {
			mapPackTtlDays = 30;
		}
		if (mapBaseUrl.endsWith("/")) {
			mapBaseUrl = mapBaseUrl.substring(0, mapBaseUrl.length() - 1);
		}
	}
}
