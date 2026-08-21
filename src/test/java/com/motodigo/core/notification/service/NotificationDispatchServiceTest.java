package com.motodigo.core.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

	@Mock
	private NotificationDispatchRepository dispatchRepository;
	@Mock
	private NotificationPreferenceRepository preferenceRepository;
	@Mock
	private NotificationDeviceRepository deviceRepository;
	@Mock
	private FcmClient fcmClient;
	@Mock
	private RealtimeHub realtimeHub;

	private final Instant now = Instant.parse("2026-08-20T10:00:00Z");
	private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
	private final PushProperties pushDisabled = new PushProperties(false, "", "");

	private NotificationDispatchService service;
	private UUID userId;
	private NotificationPreferenceRecord prefs;
	private NotificationRecord queued;

	@BeforeEach
	void setUp() {
		this.userId = UUID.randomUUID();
		this.prefs = new NotificationPreferenceRecord(UUID.randomUUID(), this.userId, true, true, true, false, this.now);
		UUID notificationId = UUID.randomUUID();
		UUID bookingId = UUID.randomUUID();
		this.queued = new NotificationRecord(notificationId, this.userId, "CONFIRMATION", "title", "body", "QUEUED",
				this.now, null, null, bookingId, null, null, this.now, "{}");
		this.service = new NotificationDispatchService(this.dispatchRepository, this.preferenceRepository,
				this.deviceRepository, this.fcmClient, this.pushDisabled, this.realtimeHub, this.clock);
	}

	@Test
	void enqueueInsertsPublishesAndMarksSentWhenFcmDisabled() {
		when(this.preferenceRepository.ensureDefaults(this.userId, this.now)).thenReturn(this.prefs);
		when(this.dispatchRepository.insertQueued(eq(this.userId), eq("CONFIRMATION"), eq("title"), eq("body"),
				eq(this.queued.bookingId()), isNull(), isNull(), eq("{}"), eq(this.now))).thenReturn(this.queued);

		Optional<NotificationRecord> result = this.service.enqueue(this.userId, "CONFIRMATION", "title", "body",
				this.queued.bookingId(), null, null, "{}");

		assertThat(result).contains(this.queued);
		verify(this.realtimeHub).publishStub(eq("notification.created"), any(Map.class));
		verify(this.dispatchRepository).markSent(this.queued.id(), this.now);
		verify(this.fcmClient, never()).send(any(), any(), any(), any());
	}

	@Test
	void enqueueSkipsWhenBookingUpdatesDisabled() {
		NotificationPreferenceRecord muted = new NotificationPreferenceRecord(UUID.randomUUID(), this.userId, true,
				false, true, false, this.now);
		when(this.preferenceRepository.ensureDefaults(this.userId, this.now)).thenReturn(muted);

		Optional<NotificationRecord> result = this.service.enqueue(this.userId, "SUBSCRIPTION", "t", "b",
				UUID.randomUUID(), null, null, "{}");

		assertThat(result).isEmpty();
		verify(this.dispatchRepository, never()).insertQueued(any(), any(), any(), any(), any(), any(), any(), any(),
				any());
	}

	@Test
	void processQueuedBatchMarksSentWithoutExternalCallWhenDisabled() {
		when(this.dispatchRepository.findQueuedDue(this.now, 50)).thenReturn(List.of(this.queued));
		when(this.preferenceRepository.ensureDefaults(this.userId, this.now)).thenReturn(this.prefs);

		int processed = this.service.processQueuedBatch(50);

		assertThat(processed).isEqualTo(1);
		verify(this.dispatchRepository).markSent(this.queued.id(), this.now);
		verify(this.fcmClient, never()).send(any(), any(), any(), any());
	}

	@Test
	void enqueueSendsFcmWhenEnabled() {
		PushProperties enabled = new PushProperties(true, "demo", "/tmp/creds.json");
		NotificationDispatchService enabledService = new NotificationDispatchService(this.dispatchRepository,
				this.preferenceRepository, this.deviceRepository, this.fcmClient, enabled, this.realtimeHub, this.clock);
		when(this.preferenceRepository.ensureDefaults(this.userId, this.now)).thenReturn(this.prefs);
		when(this.dispatchRepository.insertQueued(any(), any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(this.queued);
		when(this.deviceRepository.findTokensByUserId(this.userId)).thenReturn(List.of("token-1"));
		when(this.fcmClient.send(eq("token-1"), eq("title"), eq("body"), any())).thenReturn(true);

		enabledService.enqueue(this.userId, "CONFIRMATION", "title", "body", this.queued.bookingId(), null, null, "{}");

		ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(this.fcmClient).send(eq("token-1"), eq("title"), eq("body"), dataCaptor.capture());
		assertThat(dataCaptor.getValue()).containsEntry("type", "CONFIRMATION");
		verify(this.dispatchRepository).markSent(this.queued.id(), this.now);
	}
}
