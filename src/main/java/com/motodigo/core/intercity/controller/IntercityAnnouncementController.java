package com.motodigo.core.intercity.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.motodigo.core.intercity.dto.AnnouncementSearchCriteria;
import com.motodigo.core.intercity.dto.CreateIntercityAnnouncementRequest;
import com.motodigo.core.intercity.dto.UpdateIntercityAnnouncementRequest;
import com.motodigo.core.intercity.entity.AnnouncementSort;
import com.motodigo.core.intercity.service.IntercityService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/intercity/announcements")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class IntercityAnnouncementController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final IntercityService intercityService;

	IntercityAnnouncementController(IntercityService intercityService) {
		this.intercityService = intercityService;
	}

	@PostMapping
	ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateIntercityAnnouncementRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(this.intercityService.create(userId(jwt), activeProfileId(jwt), request));
	}

	@GetMapping
	ResponseEntity<?> search(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String departureCity,
			@RequestParam(required = false) String destinationCity,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime timeFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime timeTo,
			@RequestParam(required = false) String quarter,
			@RequestParam(defaultValue = "true") boolean onlyAvailable,
			@RequestParam(required = false) AnnouncementSort sort,
			@RequestParam(required = false) Double nearLat,
			@RequestParam(required = false) Double nearLon,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		AnnouncementSearchCriteria criteria = new AnnouncementSearchCriteria(
				departureCity,
				destinationCity,
				date,
				maxPrice,
				timeFrom,
				timeTo,
				quarter,
				onlyAvailable,
				sort == null ? AnnouncementSort.departureTime : sort,
				nearLat,
				nearLon,
				Math.max(page, 0),
				size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE));
		return ResponseEntity.ok(this.intercityService.search(criteria));
	}

	@GetMapping("/mine")
	ResponseEntity<?> listMine(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		return ResponseEntity.ok(this.intercityService.listMine(userId(jwt), activeProfileId(jwt), safePage, safeSize));
	}

	@GetMapping("/{id}")
	ResponseEntity<?> getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.intercityService.getById(userId(jwt), activeProfileId(jwt), id));
	}

	@PatchMapping("/{id}")
	ResponseEntity<?> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody UpdateIntercityAnnouncementRequest request) {
		return ResponseEntity.ok(this.intercityService.update(userId(jwt), activeProfileId(jwt), id, request));
	}

	@PostMapping("/{id}/close")
	ResponseEntity<?> close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.intercityService.close(userId(jwt), activeProfileId(jwt), id));
	}

	@PostMapping("/{id}/cancel")
	ResponseEntity<?> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok(this.intercityService.cancel(userId(jwt), activeProfileId(jwt), id));
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
