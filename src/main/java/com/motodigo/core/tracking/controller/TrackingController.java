package com.motodigo.core.tracking.controller;

import java.util.UUID;

import com.motodigo.core.tracking.dto.UpdateTripStatusRequest;
import com.motodigo.core.tracking.service.TrackingService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class TrackingController {

	private final TrackingService trackingService;

	TrackingController(TrackingService trackingService) {
		this.trackingService = trackingService;
	}

	@GetMapping("/active")
	ResponseEntity<?> getActiveTrips(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(this.trackingService.getActiveTrips(userId(jwt), activeProfileId(jwt)));
	}

	@GetMapping("/{id}")
	ResponseEntity<?> getTrip(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.trackingService.getTrip(userId(jwt), activeProfileId(jwt), id));
	}

	@PatchMapping("/{id}/status")
	ResponseEntity<?> updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody UpdateTripStatusRequest request) {
		return ResponseEntity.ok(this.trackingService.updateStatus(userId(jwt), activeProfileId(jwt), id,
				request.status()));
	}

	@PostMapping("/{id}/complete")
	ResponseEntity<?> completeTrip(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.trackingService.completeTrip(userId(jwt), activeProfileId(jwt), id));
	}

	@GetMapping("/{id}/events")
	ResponseEntity<?> getEvents(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.trackingService.getTripEvents(userId(jwt), activeProfileId(jwt), id));
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
