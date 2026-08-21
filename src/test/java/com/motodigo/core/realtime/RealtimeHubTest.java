package com.motodigo.core.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RealtimeHubTest {

	private static final UUID USER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID USER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID TRIP_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID OTHER_TRIP = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

	@Test
	void tripScopedChannelMatchesTripIdInPayload() {
		RealtimeEvent matching = tripStatus(USER_A, TRIP_ID);
		RealtimeEvent other = tripStatus(USER_A, OTHER_TRIP);

		assertThat(RealtimeHub.visibleTo("trip:" + TRIP_ID, USER_A, null, matching)).isTrue();
		assertThat(RealtimeHub.visibleTo("trip:" + TRIP_ID, USER_B, null, matching)).isTrue();
		assertThat(RealtimeHub.visibleTo("trip:" + TRIP_ID, USER_A, null, other)).isFalse();
	}

	@Test
	void genericTripChannelFiltersByUserId() {
		RealtimeEvent forUserA = tripStatus(USER_A, TRIP_ID);
		RealtimeEvent forUserB = tripStatus(USER_B, TRIP_ID);

		assertThat(RealtimeHub.visibleTo("trip", USER_A, null, forUserA)).isTrue();
		assertThat(RealtimeHub.visibleTo("trip", USER_A, null, forUserB)).isFalse();
		assertThat(RealtimeHub.visibleTo("trip", USER_B, null, forUserB)).isTrue();
	}

	@Test
	void tripChannelIgnoresNonTripEvents() {
		RealtimeEvent notification = new RealtimeEvent(
				"notification.created", null, 0, 0, Instant.parse("2026-08-20T10:00:00Z"),
				Map.of("userId", USER_A));

		assertThat(RealtimeHub.visibleTo("trip", USER_A, null, notification)).isFalse();
		assertThat(RealtimeHub.visibleTo("trip:" + TRIP_ID, USER_A, null, notification)).isFalse();
	}

	@Test
	void notificationChannelFiltersTripStatusByUserId() {
		assertThat(RealtimeHub.visibleTo("notification", USER_A, null, tripStatus(USER_A, TRIP_ID))).isTrue();
		assertThat(RealtimeHub.visibleTo("notification", USER_A, null, tripStatus(USER_B, TRIP_ID))).isFalse();
	}

	private static RealtimeEvent tripStatus(UUID userId, UUID tripId) {
		return new RealtimeEvent(
				"trip.status.updated",
				null,
				0,
				0,
				Instant.parse("2026-08-20T10:00:00Z"),
				Map.of("userId", userId, "tripId", tripId, "status", "APPROACHING"));
	}
}
