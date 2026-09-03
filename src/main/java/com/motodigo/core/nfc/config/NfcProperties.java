package com.motodigo.core.nfc.config;

import java.math.BigDecimal;
import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "motodigo.nfc")
public record NfcProperties(
		@NotBlank String signingSecret,
		@NotNull Duration ttl,
		@NotNull BigDecimal maxAmount,
		@NotNull BigDecimal offlineQuotaAmount,
		@NotNull Duration offlineQuotaTtl) {

	public NfcProperties {
		if (signingSecret == null || signingSecret.isBlank()) {
			signingSecret = "change-me-nfc-signing-secret-32b";
		}
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			ttl = Duration.ofMinutes(5);
		}
		if (maxAmount == null || maxAmount.signum() <= 0) {
			maxAmount = new BigDecimal("50000");
		}
		if (offlineQuotaAmount == null || offlineQuotaAmount.signum() < 0) {
			offlineQuotaAmount = new BigDecimal("20000");
		}
		if (offlineQuotaTtl == null || offlineQuotaTtl.isZero() || offlineQuotaTtl.isNegative()) {
			offlineQuotaTtl = Duration.ofHours(24);
		}
	}
}
