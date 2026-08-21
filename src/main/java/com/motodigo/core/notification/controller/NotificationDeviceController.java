package com.motodigo.core.notification.controller;

import java.util.UUID;

import com.motodigo.core.notification.dto.RegisterDeviceRequest;
import com.motodigo.core.notification.service.NotificationDeviceService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/devices")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class NotificationDeviceController {

	private final NotificationDeviceService service;

	NotificationDeviceController(NotificationDeviceService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<?> register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterDeviceRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(this.service.register(userId(jwt), request));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
