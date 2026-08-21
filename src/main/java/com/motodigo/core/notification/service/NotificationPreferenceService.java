package com.motodigo.core.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.notification.dto.NotificationPreferencesResponse;
import com.motodigo.core.notification.dto.UpdateNotificationPreferencesRequest;
import com.motodigo.core.notification.entity.NotificationPreferenceRecord;
import com.motodigo.core.notification.repository.NotificationPreferenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationPreferenceService {

	private final NotificationPreferenceRepository repository;
	private final Clock clock;

	@Autowired
	public NotificationPreferenceService(NotificationPreferenceRepository repository) {
		this(repository, Clock.systemUTC());
	}

	NotificationPreferenceService(NotificationPreferenceRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	@Transactional
	public NotificationPreferencesResponse get(UUID userId) {
		NotificationPreferenceRecord record = this.repository.ensureDefaults(userId, Instant.now(this.clock));
		return NotificationPreferencesResponse.from(record);
	}

	@Transactional
	public NotificationPreferencesResponse update(UUID userId, UpdateNotificationPreferencesRequest request) {
		NotificationPreferenceRecord record = this.repository.upsert(
				userId,
				request.pushEnabled(),
				request.bookingUpdates(),
				request.paymentAlerts(),
				request.marketingEnabled(),
				Instant.now(this.clock));
		return NotificationPreferencesResponse.from(record);
	}
}
