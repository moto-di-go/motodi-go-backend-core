package com.motodigo.core.support.controller;

import java.util.UUID;

import com.motodigo.core.support.dto.SupportTicketCreateRequest;
import com.motodigo.core.support.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support/tickets")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class SupportController {

	private final SupportService supportService;

	SupportController(SupportService supportService) {
		this.supportService = supportService;
	}

	@PostMapping
	ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SupportTicketCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(this.supportService.createTicket(userId(jwt), request));
	}

	@GetMapping
	ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(this.supportService.listForUser(userId(jwt), status, page, size));
	}

	@GetMapping("/{id}")
	ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.supportService.getForUser(userId(jwt), id));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
