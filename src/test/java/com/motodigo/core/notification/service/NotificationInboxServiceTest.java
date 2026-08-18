package com.motodigo.core.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.notification.entity.NotificationRecord;
import com.motodigo.core.notification.exception.NotificationErrorCode;
import com.motodigo.core.notification.exception.NotificationException;
import com.motodigo.core.notification.repository.NotificationInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceTest {

	private static final Instant FIXED_NOW = Instant.parse("2026-08-13T12:00:00Z");

	@Mock
	private NotificationInboxRepository repository;

	private NotificationInboxService service;
	private UUID userId;
	private UUID notificationId;

	@BeforeEach
	void setUp() {
		this.service = new NotificationInboxService(this.repository, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
		this.userId = UUID.randomUUID();
		this.notificationId = UUID.randomUUID();
	}

	@Test
	void listReturnsUnreadCountAndHasMore() {
		when(this.repository.findPage(this.userId, true, 3, 0))
				.thenReturn(List.of(record(false), record(false), record(false)));
		when(this.repository.countUnread(this.userId)).thenReturn(5L);

		var page = this.service.list(this.userId, true, 0, 2);

		assertThat(page.items()).hasSize(2);
		assertThat(page.hasMore()).isTrue();
		assertThat(page.unreadCount()).isEqualTo(5);
		assertThat(page.items().getFirst().unread()).isTrue();
	}

	@Test
	void markReadUpdatesOwnedNotification() {
		when(this.repository.findOwned(this.userId, this.notificationId))
				.thenReturn(Optional.of(record(false)));
		when(this.repository.markRead(eq(this.userId), eq(this.notificationId), eq(FIXED_NOW)))
				.thenReturn(Optional.of(record(true)));

		var response = this.service.markRead(this.userId, this.notificationId);

		assertThat(response.unread()).isFalse();
		assertThat(response.readAt()).isEqualTo(FIXED_NOW);
		verify(this.repository).markRead(this.userId, this.notificationId, FIXED_NOW);
	}

	@Test
	void markReadRejectsUnknownNotification() {
		when(this.repository.findOwned(this.userId, this.notificationId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.markRead(this.userId, this.notificationId))
				.isInstanceOf(NotificationException.class)
				.extracting(ex -> ((NotificationException) ex).errorCode())
				.isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
	}

	@Test
	void markAllReadReloadsInbox() {
		when(this.repository.markAllRead(this.userId, FIXED_NOW)).thenReturn(2);
		when(this.repository.findPage(this.userId, false, 21, 0)).thenReturn(List.of(record(true)));
		when(this.repository.countUnread(this.userId)).thenReturn(0L);

		var page = this.service.markAllRead(this.userId);

		assertThat(page.unreadCount()).isZero();
		assertThat(page.items()).hasSize(1);
		verify(this.repository).markAllRead(this.userId, FIXED_NOW);
	}

	private NotificationRecord record(boolean read) {
		return new NotificationRecord(
				this.notificationId, this.userId, "KYC_DECISION", "Profil mototaxi valide",
				"Vos fonctions conducteur sont actives.", read ? "READ" : "QUEUED",
				FIXED_NOW, null, read ? FIXED_NOW : null, null, null, null, FIXED_NOW,
				"{\"type\":\"KYC_DECISION\",\"status\":\"VALIDATED\"}");
	}
}
