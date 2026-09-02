package com.motodigo.core.analytics.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.analytics.exception.AnalyticsErrorCode;
import com.motodigo.core.analytics.exception.AnalyticsException;
import com.motodigo.core.analytics.service.AnalyticsService;
import com.motodigo.core.analytics.service.AnalyticsService.AdminAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/kpi")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class AdminKpiController {

	private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

	private final AnalyticsService analyticsService;

	AdminKpiController(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	@GetMapping
	ResponseEntity<?> getKpi(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String period,
			HttpServletRequest request) {
		return ResponseEntity.ok(this.analyticsService.computeKpi(adminAccess(jwt, request), from, to, period));
	}

	@GetMapping("/export.csv")
	ResponseEntity<?> exportCsv(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String period,
			HttpServletRequest request) {
		String body = this.analyticsService.exportCsv(adminAccess(jwt, request), from, to, period);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"kpi-export.csv\"")
				.contentType(new MediaType("text", "csv"))
				.body(body);
	}

	private static AdminAccess adminAccess(Jwt jwt, HttpServletRequest request) {
		return new AdminAccess(activeProfileId(jwt), jwt.getClaimAsString("activeProfileType"),
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
			throw new AnalyticsException(AnalyticsErrorCode.ANALYTICS_ACCESS_DENIED);
		}
	}

	private static List<String> permissions(Jwt jwt) {
		List<String> values = jwt.getClaimAsStringList("permissions");
		return values == null ? List.of() : values;
	}
}
