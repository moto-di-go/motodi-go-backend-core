package com.motodigo.core.payment.controller;

import java.util.UUID;

import com.motodigo.core.payment.service.ReceiptService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/receipts")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class ReceiptController {

	private final ReceiptService receiptService;

	ReceiptController(ReceiptService receiptService) {
		this.receiptService = receiptService;
	}

	@GetMapping
	ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(this.receiptService.listForUser(userId(jwt), page, size));
	}

	@GetMapping("/{id}")
	ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.receiptService.getForUser(userId(jwt), id));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
