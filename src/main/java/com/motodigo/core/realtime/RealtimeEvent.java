package com.motodigo.core.realtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RealtimeEvent(
		String type,
		UUID driverProfileId,
		double lat,
		double lon,
		Instant occurredAt,
		Map<String, Object> payload) {
}
