package com.motodigo.core.nfc.controller;

import java.util.UUID;

import com.motodigo.core.nfc.dto.NfcTokenIssueRequest;
import com.motodigo.core.nfc.service.NfcPaymentService;
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
@RequestMapping("/api/v1/nfc")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class NfcController {

	private final NfcPaymentService nfcPaymentService;

	NfcController(NfcPaymentService nfcPaymentService) {
		this.nfcPaymentService = nfcPaymentService;
	}

	@GetMapping("/eligibility")
	ResponseEntity<?> eligibility(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(this.nfcPaymentService.getEligibility(userId(jwt)));
	}

	@PostMapping("/tokens")
	ResponseEntity<?> issue(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NfcTokenIssueRequest request,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(this.nfcPaymentService.issueToken(userId(jwt), activeProfileId(jwt), request, idempotencyKey));
	}

	@GetMapping("/tokens/{id}")
	ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.nfcPaymentService.getToken(userId(jwt), id));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	private static UUID activeProfileId(Jwt jwt) {
		Object claim = jwt.getClaim("activeProfileId");
		if (claim == null) {
			return null;
		}
		return UUID.fromString(String.valueOf(claim));
	}
}
