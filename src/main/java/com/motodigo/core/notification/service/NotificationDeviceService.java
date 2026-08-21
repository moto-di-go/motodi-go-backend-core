package com.motodigo.core.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.notification.dto.DeviceResponse;
import com.motodigo.core.notification.dto.RegisterDeviceRequest;
import com.motodigo.core.notification.entity.NotificationDeviceRecord;
import com.motodigo.core.notification.exception.NotificationErrorCode;
import com.motodigo.core.notification.exception.NotificationException;
import com.motodigo.core.notification.repository.NotificationDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationDeviceService {

	private final NotificationDeviceRepository repository;
	private final Clock clock;

	@Autowired
	public NotificationDeviceService(NotificationDeviceRepository repository) {
		this(repository, Clock.systemUTC());
	}

	NotificationDeviceService(NotificationDeviceRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	@Transactional
	public DeviceResponse register(UUID userId, RegisterDeviceRequest request) {
		String token = request.token() == null ? "" : request.token().trim();
		if (token.isEmpty()) {
			throw new NotificationException(NotificationErrorCode.DEVICE_TOKEN_INVALID);
		}
		NotificationDeviceRecord saved = this.repository.upsert(userId, token, request.platform(),
				Instant.now(this.clock));
		return DeviceResponse.from(saved);
	}
}
