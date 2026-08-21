package com.motodigo.core.notification.push;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Used when {@code motodigo.push.enabled=false}: inbox-only path, no external FCM call.
 */
@Component
@ConditionalOnProperty(prefix = "motodigo.push", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpFcmClient implements FcmClient {

	private static final Logger log = LoggerFactory.getLogger(NoOpFcmClient.class);

	@Override
	public boolean send(String deviceToken, String title, String body, Map<String, String> data) {
		log.debug("FCM disabled — skipping push tokenPrefix={}", truncate(deviceToken));
		return true;
	}

	private static String truncate(String token) {
		if (token == null || token.length() < 8) {
			return "n/a";
		}
		return token.substring(0, 8) + "…";
	}
}
