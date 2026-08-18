package com.motodigo.core.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class RealtimeWebSocketConfig implements WebSocketConfigurer {

	private final RealtimeWebSocketHandler handler;

	RealtimeWebSocketConfig(RealtimeWebSocketHandler handler) {
		this.handler = handler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(this.handler, "/api/v1/realtime/ws")
				.setAllowedOriginPatterns("*");
	}
}
