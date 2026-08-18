package com.motodigo.core.pricing.config;

import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "motodigo.pricing")
public record PricingProperties(
		int fareTtlMinutes,
		LocalTime nightStart,
		LocalTime nightEnd,
		int weatherCacheSeconds,
		double rainThresholdMm) {

	public PricingProperties {
		if (fareTtlMinutes <= 0) {
			fareTtlMinutes = 210;
		}
		if (nightStart == null) {
			nightStart = LocalTime.of(22, 0);
		}
		if (nightEnd == null) {
			nightEnd = LocalTime.of(5, 0);
		}
		if (weatherCacheSeconds <= 0) {
			weatherCacheSeconds = 300;
		}
		if (rainThresholdMm <= 0) {
			rainThresholdMm = 0.2;
		}
	}

	public boolean isNight(LocalTime time) {
		if (!this.nightStart.isAfter(this.nightEnd)) {
			return !time.isBefore(this.nightStart) && time.isBefore(this.nightEnd);
		}
		return !time.isBefore(this.nightStart) || time.isBefore(this.nightEnd);
	}
}
