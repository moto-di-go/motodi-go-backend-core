package com.motodigo.core.notification.push;

import java.util.Map;

/**
 * Port for FCM delivery. Implementations may no-op when push is disabled.
 */
public interface FcmClient {

	/**
	 * @return true if the message was accepted by the provider (or intentionally skipped as inbox-only)
	 */
	boolean send(String deviceToken, String title, String body, Map<String, String> data);
}
