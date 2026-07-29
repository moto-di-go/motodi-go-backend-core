package com.motodigo.core.wallet.controller;

import java.util.UUID;

import com.motodigo.core.wallet.service.WalletService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class WalletController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final WalletService walletService;

	WalletController(WalletService walletService) {
		this.walletService = walletService;
	}

	@GetMapping
	ResponseEntity<?> getWallet(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(this.walletService.getWallet(userId(jwt)));
	}

	@GetMapping("/ledger")
	ResponseEntity<?> getLedger(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		return ResponseEntity.ok(this.walletService.getLedger(userId(jwt), safePage, safeSize));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
