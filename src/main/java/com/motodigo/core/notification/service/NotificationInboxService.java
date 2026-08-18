package com.motodigo.core.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.notification.dto.NotificationPageResponse;
import com.motodigo.core.notification.dto.NotificationResponse;
import com.motodigo.core.notification.entity.NotificationRecord;
import com.motodigo.core.notification.exception.NotificationErrorCode;
import com.motodigo.core.notification.exception.NotificationException;
import com.motodigo.core.notification.repository.NotificationInboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationInboxService {

	private final NotificationInboxRepository repository;
	private final Clock clock;

	@Autowired
	public NotificationInboxService(NotificationInboxRepository repository) {
		this(repository, Clock.systemUTC());
	}

	NotificationInboxService(NotificationInboxRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	@Transactional(readOnly = true)
	public NotificationPageResponse list(UUID userId, boolean unreadOnly, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? 20 : Math.min(size, 100);
		List<NotificationRecord> rows = this.repository.findPage(userId, unreadOnly, safeSize + 1, safePage * safeSize);
		boolean hasMore = rows.size() > safeSize;
		List<NotificationResponse> items = rows.stream().limit(safeSize).map(NotificationResponse::from).toList();
		return new NotificationPageResponse(items, safePage, safeSize, hasMore, this.repository.countUnread(userId));
	}

	@Transactional
	public NotificationResponse markRead(UUID userId, UUID notificationId) {
		this.repository.findOwned(userId, notificationId)
				.orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
		NotificationRecord updated = this.repository.markRead(userId, notificationId, Instant.now(this.clock))
				.orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
		return NotificationResponse.from(updated);
	}

	@Transactional
	public NotificationPageResponse markAllRead(UUID userId) {
		this.repository.markAllRead(userId, Instant.now(this.clock));
		return list(userId, false, 0, 20);
	}
}
