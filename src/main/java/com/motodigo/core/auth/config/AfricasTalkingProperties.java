package com.motodigo.core.auth.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "motodigo.africas-talking")
public record AfricasTalkingProperties(
		URI baseUrl,
		String username,
		String apiKey,
		String senderId,
		int enqueue,
		int connectTimeoutMs,
		int readTimeoutMs) {

	public AfricasTalkingProperties {
		if (baseUrl == null) {
			baseUrl = URI.create("https://api.africastalking.com");
		}
		if (username == null) {
			username = "";
		}
		if (apiKey == null) {
			apiKey = "";
		}
		if (senderId == null) {
			senderId = "";
		}
		if (enqueue != 0) {
			enqueue = 1;
		}
		if (connectTimeoutMs <= 0) {
			connectTimeoutMs = 3000;
		}
		if (readTimeoutMs <= 0) {
			readTimeoutMs = 5000;
		}
	}
}
