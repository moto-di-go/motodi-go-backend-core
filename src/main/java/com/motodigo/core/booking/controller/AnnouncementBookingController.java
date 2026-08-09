package com.motodigo.core.booking.controller;

import java.util.UUID;

import com.motodigo.core.booking.dto.CreateBookingRequest;
import com.motodigo.core.booking.service.BookingService;
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
@RequestMapping("/api/v1/intercity/announcements")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class AnnouncementBookingController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final BookingService bookingService;

	AnnouncementBookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@PostMapping("/{id}/bookings")
	ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody CreateBookingRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(this.bookingService.requestBooking(userId(jwt), activeProfileId(jwt), id, request));
	}

	@GetMapping("/{id}/bookings")
	ResponseEntity<?> listForAnnouncement(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		return ResponseEntity.ok(this.bookingService.listForAnnouncement(userId(jwt), activeProfileId(jwt), id,
				safePage, safeSize));
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
