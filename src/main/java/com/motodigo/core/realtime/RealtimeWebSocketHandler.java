package com.motodigo.core.realtime;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

	private final RealtimeHub realtimeHub;
	private final JwtDecoder jwtDecoder;

	public RealtimeWebSocketHandler(RealtimeHub realtimeHub, JwtDecoder jwtDecoder) {
		this.realtimeHub = realtimeHub;
		this.jwtDecoder = jwtDecoder;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		Jwt jwt = decode(session);
		if (jwt == null) {
			session.close(CloseStatus.NOT_ACCEPTABLE.withReason("JWT required"));
			return;
		}
		UUID userId = UUID.fromString(jwt.getSubject());
		UUID activeProfileId = activeProfileId(jwt);
		String channel = query(session, "channel", "yaounde");
		this.realtimeHub.registerWebSocket(session, userId, activeProfileId, channel);
		session.sendMessage(new TextMessage("{\"type\":\"ready\",\"channel\":\"" + channel + "\"}"));
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		this.realtimeHub.unregisterWebSocket(session);
	}

	private Jwt decode(WebSocketSession session) {
		String token = query(session, "access_token", null);
		if (token == null || token.isBlank()) {
			String header = session.getHandshakeHeaders().getFirst("Authorization");
			if (header != null && header.startsWith("Bearer ")) {
				token = header.substring(7);
			}
		}
		if (token == null || token.isBlank()) {
			return null;
		}
		try {
			return this.jwtDecoder.decode(token);
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private static String query(WebSocketSession session, String name, String defaultValue) {
		URI uri = session.getUri();
		if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
			return defaultValue;
		}
		for (String pair : uri.getRawQuery().split("&")) {
			int separator = pair.indexOf('=');
			String key = separator >= 0 ? pair.substring(0, separator) : pair;
			String value = separator >= 0 ? pair.substring(separator + 1) : "";
			if (name.equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
				String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
				return decoded.isBlank() ? defaultValue : decoded;
			}
		}
		return defaultValue;
	}

	private static UUID activeProfileId(Jwt jwt) {
		String claim = jwt.getClaimAsString("activeProfileId");
		if (claim == null || claim.isBlank()) {
			return null;
		}
		return UUID.fromString(claim);
	}
}
