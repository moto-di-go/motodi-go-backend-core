package com.motodigo.core.notification.controller;

import java.util.UUID;

import com.motodigo.core.notification.dto.UpdateNotificationPreferencesRequest;
import com.motodigo.core.notification.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/preferences")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class NotificationPreferenceController {

	private final NotificationPreferenceService service;

	NotificationPreferenceController(NotificationPreferenceService service) {
		this.service = service;
	}

	@GetMapping
	ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(this.service.get(userId(jwt)));
	}

	@PutMapping
	ResponseEntity<?> update(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UpdateNotificationPreferencesRequest request) {
		return ResponseEntity.ok(this.service.update(userId(jwt), request));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
