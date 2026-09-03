package com.motodigo.core.offline.controller;

import java.util.UUID;

import com.motodigo.core.offline.dto.OfflineSyncRequest;
import com.motodigo.core.offline.service.OfflineSyncService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/offline")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class OfflineController {

	private final OfflineSyncService offlineSyncService;

	OfflineController(OfflineSyncService offlineSyncService) {
		this.offlineSyncService = offlineSyncService;
	}

	@PostMapping("/sync")
	ResponseEntity<?> sync(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OfflineSyncRequest request) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(this.offlineSyncService.sync(userId(jwt), request));
	}

	@GetMapping("/operations")
	ResponseEntity<?> operations(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(this.offlineSyncService.listOperations(userId(jwt), status, page, size));
	}

	@GetMapping("/map-areas")
	ResponseEntity<?> mapAreas(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(this.offlineSyncService.mapAreas());
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
