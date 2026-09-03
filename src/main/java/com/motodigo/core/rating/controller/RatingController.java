package com.motodigo.core.rating.controller;

import java.util.UUID;

import com.motodigo.core.rating.dto.RatingRequest;
import com.motodigo.core.rating.service.RatingService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{id}/ratings")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class RatingController {

	private final RatingService ratingService;

	RatingController(RatingService ratingService) {
		this.ratingService = ratingService;
	}

	@PostMapping
	ResponseEntity<?> rate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody RatingRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(this.ratingService.rateTrip(userId(jwt), activeProfileId(jwt), id, request));
	}

	@GetMapping
	ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.ratingService.listForTrip(userId(jwt), activeProfileId(jwt), id));
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
