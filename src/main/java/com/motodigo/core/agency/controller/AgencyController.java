package com.motodigo.core.agency.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.agency.exception.AgencyErrorCode;
import com.motodigo.core.agency.exception.AgencyException;
import com.motodigo.core.agency.service.AgencyService;
import com.motodigo.core.agency.service.AgencyService.AgencyAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agency")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class AgencyController {

	private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final AgencyService agencyService;

	AgencyController(AgencyService agencyService) {
		this.agencyService = agencyService;
	}

	@GetMapping("/overview")
	ResponseEntity<?> overview(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			HttpServletRequest request) {
		return ResponseEntity.ok(this.agencyService.overview(agencyAccess(jwt, request), from, to));
	}

	@GetMapping("/announcements")
	ResponseEntity<?> listAnnouncements(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			HttpServletRequest request) {
		return ResponseEntity.ok(this.agencyService.listAnnouncements(
				agencyAccess(jwt, request), status, safePage(page), safeSize(size)));
	}

	@GetMapping("/bookings")
	ResponseEntity<?> listBookings(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			HttpServletRequest request) {
		return ResponseEntity.ok(this.agencyService.listBookings(
				agencyAccess(jwt, request), status, safePage(page), safeSize(size)));
	}

	private static int safePage(int page) {
		return Math.max(page, 0);
	}

	private static int safeSize(int size) {
		return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
	}

	private static AgencyAccess agencyAccess(Jwt jwt, HttpServletRequest request) {
		return new AgencyAccess(activeProfileId(jwt), jwt.getClaimAsString("activeProfileType"),
				permissions(jwt), request.getHeader(CORRELATION_ID_HEADER));
	}

	private static UUID activeProfileId(Jwt jwt) {
		String value = jwt.getClaimAsString("activeProfileId");
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException ex) {
			throw new AgencyException(AgencyErrorCode.AGENCY_ACCESS_DENIED);
		}
	}

	private static List<String> permissions(Jwt jwt) {
		List<String> values = jwt.getClaimAsStringList("permissions");
		return values == null ? List.of() : values;
	}
}
