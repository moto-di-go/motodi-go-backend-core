package com.motodigo.core.payment.controller;

import java.util.UUID;

import com.motodigo.core.payment.dto.DepositRequest;
import com.motodigo.core.payment.service.PaymentService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet/deposits")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class WalletDepositController {

	private final PaymentService paymentService;

	WalletDepositController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping
	ResponseEntity<?> initiateDeposit(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody DepositRequest request,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(this.paymentService.initiateDeposit(userId(jwt), request, idempotencyKey));
	}

	@GetMapping("/{depositId}")
	ResponseEntity<?> getDeposit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID depositId) {
		return ResponseEntity.ok(this.paymentService.getDepositForUser(userId(jwt), depositId));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
