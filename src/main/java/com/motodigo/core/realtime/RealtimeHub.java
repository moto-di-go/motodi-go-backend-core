package com.motodigo.core.realtime;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motodigo.core.geo.event.DriverPositionUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RealtimeHub {

	private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);
	private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;
	static final List<String> POLLING_FALLBACKS = List.of(
			"GET /api/v1/drivers/position",
			"GET /api/v1/trips/{id}",
			"GET /api/v1/trips/{id}/events");

	private final List<SseSubscription> sseSubscriptions = new CopyOnWriteArrayList<>();
	private final List<WebSocketSubscription> wsSubscriptions = new CopyOnWriteArrayList<>();
	private final ObjectMapper objectMapper;

	public RealtimeHub(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public SseEmitter subscribeSse(UUID userId, UUID activeProfileId, String channel) {
		SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
		SseSubscription subscription = new SseSubscription(userId, activeProfileId, normalize(channel), emitter);
		this.sseSubscriptions.add(subscription);
		emitter.onCompletion(() -> this.sseSubscriptions.remove(subscription));
		emitter.onTimeout(() -> this.sseSubscriptions.remove(subscription));
		emitter.onError(error -> this.sseSubscriptions.remove(subscription));
		try {
			Map<String, Object> ready = new HashMap<>();
			ready.put("channel", subscription.channel());
			ready.put("fallback", POLLING_FALLBACKS);
			emitter.send(SseEmitter.event().name("ready").data(ready));
		}
		catch (IOException ex) {
			this.sseSubscriptions.remove(subscription);
		}
		return emitter;
	}

	public void registerWebSocket(WebSocketSession session, UUID userId, UUID activeProfileId, String channel) {
		this.wsSubscriptions.add(new WebSocketSubscription(userId, activeProfileId, normalize(channel), session));
	}

	public void unregisterWebSocket(WebSocketSession session) {
		this.wsSubscriptions.removeIf(subscription -> subscription.session().getId().equals(session.getId()));
	}

	@EventListener
	public void onDriverPositionUpdated(DriverPositionUpdatedEvent event) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("id", event.position().id());
		payload.put("speedKmh", event.position().speedKmh() == null ? 0d : event.position().speedKmh());
		payload.put("accuracyMeters",
				event.position().accuracyMeters() == null ? 0d : event.position().accuracyMeters());
		RealtimeEvent realtimeEvent = new RealtimeEvent(
				"driver.position.updated",
				event.position().driverProfileId(),
				event.position().lat(),
				event.position().lon(),
				event.position().capturedAt(),
				payload);
		broadcast(realtimeEvent);
	}

	public void publishStub(String type, Map<String, Object> payload) {
		broadcast(new RealtimeEvent(type, null, 0, 0, java.time.Instant.now(),
				payload == null ? Map.of() : payload));
	}

	private void broadcast(RealtimeEvent event) {
		String json;
		try {
			json = this.objectMapper.writeValueAsString(event);
		}
		catch (JsonProcessingException ex) {
			log.warn("Unable to serialize realtime event {}", event.type());
			return;
		}
		this.sseSubscriptions.removeIf(subscription -> !sendSse(subscription, event, json));
		this.wsSubscriptions.removeIf(subscription -> !sendWs(subscription, event, json));
	}

	private boolean sendSse(SseSubscription subscription, RealtimeEvent event, String json) {
		if (!visibleTo(subscription.channel(), subscription.userId(), subscription.activeProfileId(), event)) {
			return true;
		}
		try {
			subscription.emitter().send(SseEmitter.event().name(event.type()).data(json));
			return true;
		}
		catch (IOException ex) {
			subscription.emitter().complete();
			return false;
		}
	}

	private boolean sendWs(WebSocketSubscription subscription, RealtimeEvent event, String json) {
		if (!subscription.session().isOpen()) {
			return false;
		}
		if (!visibleTo(subscription.channel(), subscription.userId(), subscription.activeProfileId(), event)) {
			return true;
		}
		try {
			subscription.session().sendMessage(new TextMessage(json));
			return true;
		}
		catch (IOException ex) {
			return false;
		}
	}

	/**
	 * Channel visibility rules:
	 * <ul>
	 *   <li>{@code notification} — {@code notification.created} / {@code trip.status.updated} filtered by payload {@code userId}</li>
	 *   <li>{@code trip} — {@code trip.status.updated} filtered by payload {@code userId}</li>
	 *   <li>{@code trip:{uuid}} — {@code trip.status.updated} filtered by payload {@code tripId}</li>
	 *   <li>geo channels (e.g. {@code yaounde}) — driver position for self or city feed</li>
	 * </ul>
	 */
	static boolean visibleTo(String channel, UUID userId, UUID activeProfileId, RealtimeEvent event) {
		if ("notification".equals(channel)) {
			if (!"notification.created".equals(event.type()) && !"trip.status.updated".equals(event.type())) {
				return false;
			}
			return matchesPayloadUser(userId, event);
		}
		if ("trip".equals(channel)) {
			if (!"trip.status.updated".equals(event.type())) {
				return false;
			}
			return matchesPayloadUser(userId, event);
		}
		if (channel != null && channel.startsWith("trip:")) {
			if (!"trip.status.updated".equals(event.type())) {
				return false;
			}
			String expectedTripId = channel.substring("trip:".length());
			Object tripId = event.payload() == null ? null : event.payload().get("tripId");
			return tripId != null && expectedTripId.equalsIgnoreCase(String.valueOf(tripId));
		}
		if (event.driverProfileId() == null) {
			return true;
		}
		return "yaounde".equals(channel) || event.driverProfileId().equals(activeProfileId);
	}

	private static boolean matchesPayloadUser(UUID userId, RealtimeEvent event) {
		Object payloadUserId = event.payload() == null ? null : event.payload().get("userId");
		if (payloadUserId != null && userId != null) {
			return userId.toString().equals(String.valueOf(payloadUserId));
		}
		return "notification.created".equals(event.type()) && payloadUserId == null;
	}

	private static String normalize(String channel) {
		if (channel == null || channel.isBlank()) {
			return "yaounde";
		}
		String normalized = channel.toLowerCase(Locale.ROOT).trim();
		if (normalized.startsWith("trip:")) {
			String id = normalized.substring("trip:".length()).trim();
			return id.isEmpty() ? "trip" : "trip:" + id;
		}
		return normalized;
	}

	private record SseSubscription(UUID userId, UUID activeProfileId, String channel, SseEmitter emitter) {
	}

	private record WebSocketSubscription(UUID userId, UUID activeProfileId, String channel, WebSocketSession session) {
	}
}
