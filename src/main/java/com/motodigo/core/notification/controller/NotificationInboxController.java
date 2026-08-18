package com.motodigo.core.notification.controller;

import java.util.UUID;

import com.motodigo.core.notification.service.NotificationInboxService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class NotificationInboxController {

	private final NotificationInboxService service;

	NotificationInboxController(NotificationInboxService service) {
		this.service = service;
	}

	@GetMapping
	ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "false") boolean unreadOnly,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(this.service.list(userId(jwt), unreadOnly, page, size));
	}

	@PostMapping("/{id}/read")
	ResponseEntity<?> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.service.markRead(userId(jwt), id));
	}

	@PostMapping("/read-all")
	ResponseEntity<?> markAllRead(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(this.service.markAllRead(userId(jwt)));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
