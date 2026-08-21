package com.motodigo.core.notification.push;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.motodigo.core.notification.config.PushProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "motodigo.push", name = "enabled", havingValue = "true")
public class FirebaseAdminFcmClient implements FcmClient {

	private static final Logger log = LoggerFactory.getLogger(FirebaseAdminFcmClient.class);

	private final FirebaseMessaging messaging;

	public FirebaseAdminFcmClient(PushProperties properties) throws IOException {
		FirebaseApp app = ensureApp(properties);
		this.messaging = FirebaseMessaging.getInstance(app);
	}

	FirebaseAdminFcmClient(FirebaseMessaging messaging) {
		this.messaging = messaging;
	}

	@Override
	public boolean send(String deviceToken, String title, String body, Map<String, String> data) {
		if (deviceToken == null || deviceToken.isBlank()) {
			return false;
		}
		Message.Builder builder = Message.builder()
				.setToken(deviceToken)
				.setNotification(Notification.builder().setTitle(title).setBody(body).build());
		if (data != null && !data.isEmpty()) {
			builder.putAllData(data);
		}
		try {
			this.messaging.send(builder.build());
			return true;
		}
		catch (FirebaseMessagingException ex) {
			log.warn("FCM send failed tokenPrefix={} code={}", truncate(deviceToken), ex.getMessagingErrorCode(), ex);
			return false;
		}
	}

	private static FirebaseApp ensureApp(PushProperties properties) throws IOException {
		if (!FirebaseApp.getApps().isEmpty()) {
			return FirebaseApp.getInstance();
		}
		if (properties.credentialsPath() == null || properties.credentialsPath().isBlank()) {
			throw new IllegalStateException("FCM_CREDENTIALS_PATH is required when FCM_ENABLED=true");
		}
		try (FileInputStream credentials = new FileInputStream(properties.credentialsPath())) {
			FirebaseOptions.Builder options = FirebaseOptions.builder()
					.setCredentials(GoogleCredentials.fromStream(credentials));
			if (properties.projectId() != null && !properties.projectId().isBlank()) {
				options.setProjectId(properties.projectId());
			}
			return FirebaseApp.initializeApp(options.build());
		}
	}

	private static String truncate(String token) {
		if (token == null || token.length() < 8) {
			return "n/a";
		}
		return token.substring(0, 8) + "…";
	}
}
