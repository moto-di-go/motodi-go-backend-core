package com.motodigo.core.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.notification.config.PushProperties;
import com.motodigo.core.notification.entity.NotificationPreferenceRecord;
import com.motodigo.core.notification.entity.NotificationRecord;
import com.motodigo.core.notification.push.FcmClient;
import com.motodigo.core.notification.repository.NotificationDeviceRepository;
import com.motodigo.core.notification.repository.NotificationDispatchRepository;
import com.motodigo.core.notification.repository.NotificationPreferenceRepository;
import com.motodigo.core.realtime.RealtimeHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationDispatchService {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

	private final NotificationDispatchRepository dispatchRepository;
	private final NotificationPreferenceRepository preferenceRepository;
	private final NotificationDeviceRepository deviceRepository;
	private final FcmClient fcmClient;
	private final PushProperties pushProperties;
	private final RealtimeHub realtimeHub;
	private final Clock clock;

	@Autowired
	public NotificationDispatchService(NotificationDispatchRepository dispatchRepository,
			NotificationPreferenceRepository preferenceRepository,
			NotificationDeviceRepository deviceRepository,
			FcmClient fcmClient,
			PushProperties pushProperties,
			RealtimeHub realtimeHub) {
		this(dispatchRepository, preferenceRepository, deviceRepository, fcmClient, pushProperties, realtimeHub,
				Clock.systemUTC());
	}

	NotificationDispatchService(NotificationDispatchRepository dispatchRepository,
			NotificationPreferenceRepository preferenceRepository,
			NotificationDeviceRepository deviceRepository,
			FcmClient fcmClient,
			PushProperties pushProperties,
			RealtimeHub realtimeHub,
			Clock clock) {
		this.dispatchRepository = dispatchRepository;
		this.preferenceRepository = preferenceRepository;
		this.deviceRepository = deviceRepository;
		this.fcmClient = fcmClient;
		this.pushProperties = pushProperties;
		this.realtimeHub = realtimeHub;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	/**
	 * Inserts a QUEUED inbox row (if preferences allow), publishes realtime, then attempts push dispatch.
	 *
	 * @return empty when preference category disabled
	 */
	@Transactional
	public Optional<NotificationRecord> enqueue(UUID userId, String type, String title, String body, UUID bookingId,
			UUID paymentTransactionId, UUID tripId, String metadataJson) {
		Instant now = Instant.now(this.clock);
		NotificationPreferenceRecord prefs = this.preferenceRepository.ensureDefaults(userId, now);
		if (!allowsCategory(prefs, type)) {
			log.debug("Skipping notification type={} for userId={} (preferences)", type, userId);
			return Optional.empty();
		}

		NotificationRecord queued = this.dispatchRepository.insertQueued(userId, type, title, body, bookingId,
				paymentTransactionId, tripId, metadataJson, now);
		publishCreated(queued);
		dispatch(queued, prefs);
		return Optional.of(queued);
	}

	@Transactional
	public int processQueuedBatch(int limit) {
		Instant now = Instant.now(this.clock);
		List<NotificationRecord> due = this.dispatchRepository.findQueuedDue(now, Math.max(1, Math.min(limit, 200)));
		int processed = 0;
		for (NotificationRecord record : due) {
			NotificationPreferenceRecord prefs = this.preferenceRepository.ensureDefaults(record.userId(), now);
			if (dispatch(record, prefs)) {
				processed++;
			}
		}
		return processed;
	}

	private boolean dispatch(NotificationRecord record, NotificationPreferenceRecord prefs) {
		Instant now = Instant.now(this.clock);
		boolean pushWanted = prefs.pushEnabled() && allowsCategory(prefs, record.type());
		List<String> tokens = pushWanted ? this.deviceRepository.findTokensByUserId(record.userId()) : List.of();

		// FCM disabled or no push wanted / no tokens → inbox-only SENT (no external call required).
		if (!this.pushProperties.enabled() || !pushWanted || tokens.isEmpty()) {
			this.dispatchRepository.markSent(record.id(), now);
			return true;
		}

		Map<String, String> data = new HashMap<>();
		data.put("notificationId", record.id().toString());
		data.put("type", record.type());
		if (record.bookingId() != null) {
			data.put("bookingId", record.bookingId().toString());
		}
		if (record.paymentTransactionId() != null) {
			data.put("paymentTransactionId", record.paymentTransactionId().toString());
		}

		boolean anySuccess = false;
		boolean anyFailure = false;
		for (String token : tokens) {
			boolean ok = this.fcmClient.send(token, record.title(), record.body(), data);
			if (ok) {
				anySuccess = true;
			}
			else {
				anyFailure = true;
			}
		}

		if (anySuccess || !anyFailure) {
			this.dispatchRepository.markSent(record.id(), now);
			return true;
		}
		this.dispatchRepository.markFailed(record.id());
		return false;
	}

	private void publishCreated(NotificationRecord record) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("id", record.id());
		payload.put("userId", record.userId());
		payload.put("type", record.type());
		payload.put("title", record.title());
		payload.put("body", record.body());
		payload.put("bookingId", record.bookingId());
		payload.put("paymentTransactionId", record.paymentTransactionId());
		payload.put("tripId", record.tripId());
		payload.put("createdAt", record.createdAt() == null ? Instant.now(this.clock).toString()
				: record.createdAt().toString());
		this.realtimeHub.publishStub("notification.created", payload);
	}

	private static boolean allowsCategory(NotificationPreferenceRecord prefs, String type) {
		if (type == null) {
			return true;
		}
		return switch (type) {
			case "SUBSCRIPTION", "CONFIRMATION", "CANCELLATION", "EXPIRATION", "DEPARTURE_REMINDER", "STATUS_CHANGE",
					"TRIP_COMPLETED" -> prefs.bookingUpdates();
			case "PAYMENT", "RATING" -> prefs.paymentAlerts();
			case "SUPPORT", "KYC_DECISION" -> true;
			default -> true;
		};
	}
}
