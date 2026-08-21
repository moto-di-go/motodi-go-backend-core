package com.motodigo.core.realtime;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/realtime")
class RealtimeController {

	private final RealtimeHub realtimeHub;

	RealtimeController(RealtimeHub realtimeHub) {
		this.realtimeHub = realtimeHub;
	}

	@GetMapping(path = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter subscribeSse(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "yaounde") String channel) {
		requireJwt(jwt);
		return this.realtimeHub.subscribeSse(userId(jwt), activeProfileId(jwt), channel);
	}

	@GetMapping("/channels")
	Map<String, Object> channels() {
		return Map.of(
				"gps", "driver.position.updated",
				"trip", "trip.status.updated (channel=trip filters by userId; channel=trip:{uuid} by tripId)",
				"notification", "notification.created",
				"fallback", RealtimeHub.POLLING_FALLBACKS);
	}

	private static void requireJwt(Jwt jwt) {
		if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT required");
		}
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	private static UUID activeProfileId(Jwt jwt) {
		String claim = jwt.getClaimAsString("activeProfileId");
		if (claim == null || claim.isBlank()) {
			return null;
		}
		return UUID.fromString(claim);
	}
}
