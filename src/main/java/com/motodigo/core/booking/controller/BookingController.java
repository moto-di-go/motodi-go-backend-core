package com.motodigo.core.booking.controller;

import java.util.UUID;

import com.motodigo.core.booking.dto.CancelBookingRequest;
import com.motodigo.core.booking.service.BookingService;
import com.motodigo.core.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class BookingController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final BookingService bookingService;
	private final PaymentService paymentService;

	BookingController(BookingService bookingService, PaymentService paymentService) {
		this.bookingService = bookingService;
		this.paymentService = paymentService;
	}

	@GetMapping("/mine")
	ResponseEntity<?> listMine(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		return ResponseEntity.ok(this.bookingService.listMine(userId(jwt), activeProfileId(jwt), safePage, safeSize));
	}

	@GetMapping("/{id}")
	ResponseEntity<?> getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.bookingService.getBooking(userId(jwt), activeProfileId(jwt), id));
	}

	@PostMapping("/{id}/confirm")
	ResponseEntity<?> confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.bookingService.confirmBooking(userId(jwt), activeProfileId(jwt), id));
	}

	@PostMapping("/{id}/cancel")
	ResponseEntity<?> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody CancelBookingRequest request) {
		return ResponseEntity.ok(this.bookingService.cancelBooking(userId(jwt), activeProfileId(jwt), id, request));
	}

	@PostMapping("/{id}/pay")
	ResponseEntity<?> pay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		return ResponseEntity.ok(this.paymentService.payBooking(userId(jwt), activeProfileId(jwt), id, idempotencyKey));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	private static UUID activeProfileId(Jwt jwt) {
		String claim = jwt.getClaimAsString("activeProfileId");
		if (claim == null || claim.isBlank()) {
			return null;
		}
		return UUID.fromString(claim);
	}
}
