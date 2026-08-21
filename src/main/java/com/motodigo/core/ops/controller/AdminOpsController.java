package com.motodigo.core.ops.controller;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.ops.exception.OpsErrorCode;
import com.motodigo.core.ops.exception.OpsException;
import com.motodigo.core.ops.service.AdminOpsService;
import com.motodigo.core.ops.service.AdminOpsService.AdminAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ops")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class AdminOpsController {

	private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final AdminOpsService adminOpsService;

	AdminOpsController(AdminOpsService adminOpsService) {
		this.adminOpsService = adminOpsService;
	}

	@GetMapping("/overview")
	ResponseEntity<?> overview(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
		return ResponseEntity.ok(this.adminOpsService.overview(adminAccess(jwt, request)));
	}

	@GetMapping("/users")
	ResponseEntity<?> listUsers(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String format,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			HttpServletRequest request) {
		AdminAccess access = adminAccess(jwt, request);
		if (isCsv(format)) {
			return csvResponse("ops-users.csv", this.adminOpsService.usersCsv(access, status, search));
		}
		return ResponseEntity.ok(this.adminOpsService.listUsers(access, status, search, safePage(page), safeSize(size)));
	}

	@GetMapping("/users/export.csv")
	ResponseEntity<?> exportUsers(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			HttpServletRequest request) {
		return csvResponse("ops-users.csv",
				this.adminOpsService.usersCsv(adminAccess(jwt, request), status, search));
	}

	@GetMapping("/users/{userId}")
	ResponseEntity<?> getUser(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
			HttpServletRequest request) {
		return ResponseEntity.ok(this.adminOpsService.getUser(adminAccess(jwt, request), userId));
	}

	@GetMapping("/profiles")
	ResponseEntity<?> listProfiles(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String format,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			HttpServletRequest request) {
		AdminAccess access = adminAccess(jwt, request);
		if (isCsv(format)) {
			return csvResponse("ops-profiles.csv",
					this.adminOpsService.profilesCsv(access, status, type, search));
		}
		return ResponseEntity.ok(
				this.adminOpsService.listProfiles(access, status, type, search, safePage(page), safeSize(size)));
	}

	@GetMapping("/profiles/export.csv")
	ResponseEntity<?> exportProfiles(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String search,
			HttpServletRequest request) {
		return csvResponse("ops-profiles.csv",
				this.adminOpsService.profilesCsv(adminAccess(jwt, request), status, type, search));
	}

	@GetMapping("/announcements")
	ResponseEntity<?> listAnnouncements(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String format,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			HttpServletRequest request) {
		AdminAccess access = adminAccess(jwt, request);
		if (isCsv(format)) {
			return csvResponse("ops-announcements.csv",
					this.adminOpsService.announcementsCsv(access, status, search));
		}
		return ResponseEntity.ok(
				this.adminOpsService.listAnnouncements(access, status, search, safePage(page), safeSize(size)));
	}

	@GetMapping("/announcements/export.csv")
	ResponseEntity<?> exportAnnouncements(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			HttpServletRequest request) {
		return csvResponse("ops-announcements.csv",
				this.adminOpsService.announcementsCsv(adminAccess(jwt, request), status, search));
	}

	@GetMapping("/bookings")
	ResponseEntity<?> listBookings(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String format,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			HttpServletRequest request) {
		AdminAccess access = adminAccess(jwt, request);
		if (isCsv(format)) {
			return csvResponse("ops-bookings.csv", this.adminOpsService.bookingsCsv(access, status, search));
		}
		return ResponseEntity.ok(
				this.adminOpsService.listBookings(access, status, search, safePage(page), safeSize(size)));
	}

	@GetMapping("/bookings/export.csv")
	ResponseEntity<?> exportBookings(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			HttpServletRequest request) {
		return csvResponse("ops-bookings.csv",
				this.adminOpsService.bookingsCsv(adminAccess(jwt, request), status, search));
	}

	@GetMapping("/payments")
	ResponseEntity<?> listPayments(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String format,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			HttpServletRequest request) {
		AdminAccess access = adminAccess(jwt, request);
		if (isCsv(format)) {
			return csvResponse("ops-payments.csv", this.adminOpsService.paymentsCsv(access, status, search));
		}
		return ResponseEntity.ok(
				this.adminOpsService.listPayments(access, status, search, safePage(page), safeSize(size)));
	}

	@GetMapping("/payments/export.csv")
	ResponseEntity<?> exportPayments(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			HttpServletRequest request) {
		return csvResponse("ops-payments.csv",
				this.adminOpsService.paymentsCsv(adminAccess(jwt, request), status, search));
	}

	private static ResponseEntity<String> csvResponse(String filename, String body) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.contentType(new MediaType("text", "csv"))
				.body(body);
	}

	private static boolean isCsv(String format) {
		return format != null && format.equalsIgnoreCase("csv");
	}

	private static int safePage(int page) {
		return Math.max(page, 0);
	}

	private static int safeSize(int size) {
		return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
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
			throw new OpsException(OpsErrorCode.OPS_ACCESS_DENIED);
		}
	}

	private static List<String> permissions(Jwt jwt) {
		List<String> values = jwt.getClaimAsStringList("permissions");
		return values == null ? List.of() : values;
	}
}
