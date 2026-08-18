package com.motodigo.core.geo.controller;

import java.util.UUID;

import com.motodigo.core.geo.dto.DriverAvailabilityRequest;
import com.motodigo.core.geo.dto.GeoPointRequest;
import com.motodigo.core.geo.service.GeoService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class DriverGeoController {

	private final GeoService geoService;

	DriverGeoController(GeoService geoService) {
		this.geoService = geoService;
	}

	@PutMapping("/position")
	ResponseEntity<?> ingestPosition(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GeoPointRequest request) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(this.geoService.ingestDriverPosition(userId(jwt), activeProfileId(jwt), request));
	}

	@GetMapping("/position")
	ResponseEntity<?> lastPosition(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(this.geoService.lastPosition(userId(jwt), activeProfileId(jwt)));
	}

	@PatchMapping("/availability")
	ResponseEntity<?> updateAvailability(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody DriverAvailabilityRequest request) {
		return ResponseEntity.ok(this.geoService.updateAvailability(userId(jwt), activeProfileId(jwt), request));
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
