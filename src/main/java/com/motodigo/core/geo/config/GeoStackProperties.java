package com.motodigo.core.geo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "motodigo.geostack")
public record GeoStackProperties(
		boolean photonEnabled,
		String photonBaseUrl,
		int photonTimeoutMs,
		boolean valhallaEnabled,
		String valhallaBaseUrl,
		int valhallaTimeoutMs,
		String valhallaCosting,
		int valhallaAlternates) {

	public GeoStackProperties {
		if (photonBaseUrl == null || photonBaseUrl.isBlank()) {
			photonBaseUrl = "http://localhost:2322";
		}
		if (photonTimeoutMs <= 0) {
			photonTimeoutMs = 1200;
		}
		if (valhallaBaseUrl == null || valhallaBaseUrl.isBlank()) {
			valhallaBaseUrl = "http://localhost:8002";
		}
		if (valhallaTimeoutMs <= 0) {
			valhallaTimeoutMs = 2500;
		}
		if (valhallaCosting == null || valhallaCosting.isBlank()) {
			valhallaCosting = "motorcycle";
		}
		if (valhallaAlternates < 0) {
			valhallaAlternates = 2;
		}
	}

	public boolean photonReady() {
		return this.photonEnabled && this.photonBaseUrl != null && !this.photonBaseUrl.isBlank();
	}

	public boolean valhallaReady() {
		return this.valhallaEnabled && this.valhallaBaseUrl != null && !this.valhallaBaseUrl.isBlank();
	}
}
