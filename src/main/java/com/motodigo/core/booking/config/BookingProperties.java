package com.motodigo.core.booking.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "motodigo.booking")
public record BookingProperties(
		@NotNull Duration confirmationTtl,
		@NotNull Duration paymentTtl,
		@NotBlank String expirationCron) {

	public BookingProperties {
		if (confirmationTtl == null || confirmationTtl.isZero() || confirmationTtl.isNegative()) {
			confirmationTtl = Duration.ofMinutes(10);
		}
		if (paymentTtl == null || paymentTtl.isZero() || paymentTtl.isNegative()) {
			paymentTtl = Duration.ofMinutes(10);
		}
		if (expirationCron == null || expirationCron.isBlank()) {
			expirationCron = "0 * * * * *";
		}
	}
}
