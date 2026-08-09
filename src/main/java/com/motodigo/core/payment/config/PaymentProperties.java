package com.motodigo.core.payment.config;

import java.time.Duration;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "motodigo.payment")
public record PaymentProperties(
		@Valid @NotNull MobileMoney mobileMoney) {

	public PaymentProperties {
		if (mobileMoney == null) {
			mobileMoney = MobileMoney.defaults();
		}
	}

	public record MobileMoney(
			@NotBlank String mode,
			@NotNull Duration pendingTtl,
			int connectTimeoutMs,
			int readTimeoutMs,
			Map<String, Provider> providers) {

		public MobileMoney {
			if (mode == null || mode.isBlank()) {
				mode = "stub";
			}
			if (pendingTtl == null || pendingTtl.isZero() || pendingTtl.isNegative()) {
				pendingTtl = Duration.ofMinutes(30);
			}
			if (connectTimeoutMs <= 0) {
				connectTimeoutMs = 3000;
			}
			if (readTimeoutMs <= 0) {
				readTimeoutMs = 5000;
			}
			providers = providers == null || providers.isEmpty() ? defaultProviders() : Map.copyOf(providers);
		}

		static MobileMoney defaults() {
			return new MobileMoney("stub", Duration.ofMinutes(30), 3000, 5000, defaultProviders());
		}

		private static Map<String, Provider> defaultProviders() {
			return Map.of(
					"MTN_MOMO", new Provider("https://placeholder.mtn.example", "", ""),
					"ORANGE_MONEY", new Provider("https://placeholder.orange.example", "", ""),
					"AGGREGATOR", new Provider("https://placeholder.aggregator.example", "", ""));
		}

		public Provider provider(String key) {
			return providers.get(key);
		}
	}

	public record Provider(
			@NotBlank String baseUrl,
			String apiKey,
			String subscriptionKey) {

		public Provider {
			if (baseUrl == null || baseUrl.isBlank()) {
				baseUrl = "https://placeholder.example";
			}
			if (apiKey == null) {
				apiKey = "";
			}
			if (subscriptionKey == null) {
				subscriptionKey = "";
			}
		}
	}
}
