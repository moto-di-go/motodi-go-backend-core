package com.motodigo.core.auth.config;

import java.net.URI;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "motodigo.africas-talking")
public record AfricasTalkingProperties(
		URI baseUrl,
		String username,
		String apiKey,
		String senderId,
		boolean enqueue,
		int connectTimeoutMs,
		int readTimeoutMs) {

	private static final URI DEFAULT_BASE_URL = URI.create("https://api.africastalking.com");
	private static final URI SANDBOX_BASE_URL = URI.create("https://api.sandbox.africastalking.com");
	private static final String MESSAGING_PATH = "/version1/messaging";
	private static final String BULK_MESSAGING_PATH = MESSAGING_PATH + "/bulk";
	private static final String LIVE_HOST = "api.africastalking.com";
	private static final String SANDBOX_HOST = "api.sandbox.africastalking.com";

	public AfricasTalkingProperties {
		username = username == null ? "" : username.strip();
		apiKey = apiKey == null ? "" : apiKey.strip();
		senderId = senderId == null ? "" : senderId.strip();
		baseUrl = normalizeBaseUrl(baseUrl, username);
		if (connectTimeoutMs <= 0) {
			connectTimeoutMs = 3000;
		}
		if (readTimeoutMs <= 0) {
			readTimeoutMs = 5000;
		}
	}

	static URI normalizeBaseUrl(URI baseUrl) {
		return normalizeBaseUrl(baseUrl, "");
	}

	public String apiUsername() {
		return isSandboxUsername(this.username) ? "sandbox" : this.username;
	}

	public boolean sandbox() {
		return isSandboxUsername(this.username) || isSandboxHost(this.baseUrl);
	}

	public String requestFrom() {
		if (sandbox() || "sandbox".equalsIgnoreCase(this.senderId)) {
			return "";
		}
		return this.senderId;
	}

	static URI normalizeBaseUrl(URI baseUrl, String username) {
		URI normalized = stripMessagingPath(baseUrl);
		if (isSandboxUsername(username) && isLiveApiHost(normalized)) {
			return SANDBOX_BASE_URL;
		}
		return normalized;
	}

	private static URI stripMessagingPath(URI baseUrl) {
		if (baseUrl == null) {
			return DEFAULT_BASE_URL;
		}
		String value = trimTrailingSlashes(baseUrl.toString().strip());
		String lower = value.toLowerCase(Locale.ROOT);
		if (lower.endsWith(BULK_MESSAGING_PATH)) {
			value = trimTrailingSlashes(value.substring(0, value.length() - BULK_MESSAGING_PATH.length()));
		}
		else if (lower.endsWith(MESSAGING_PATH)) {
			value = trimTrailingSlashes(value.substring(0, value.length() - MESSAGING_PATH.length()));
		}
		if (value.isBlank()) {
			return DEFAULT_BASE_URL;
		}
		return URI.create(value);
	}

	private static String trimTrailingSlashes(String value) {
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}

	private static boolean isSandboxUsername(String username) {
		return "sandbox".equalsIgnoreCase(username);
	}

	private static boolean isLiveApiHost(URI baseUrl) {
		String host = baseUrl.getHost();
		return host != null && LIVE_HOST.equalsIgnoreCase(host);
	}

	private static boolean isSandboxHost(URI baseUrl) {
		String host = baseUrl.getHost();
		return host != null && SANDBOX_HOST.equalsIgnoreCase(host);
	}
}
