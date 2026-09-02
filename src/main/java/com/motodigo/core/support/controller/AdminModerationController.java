package com.motodigo.core.support.controller;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.support.dto.ModerationActionRequest;
import com.motodigo.core.support.exception.SupportErrorCode;
import com.motodigo.core.support.exception.SupportException;
import com.motodigo.core.support.service.SupportService;
import com.motodigo.core.support.service.SupportService.AdminAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/moderation")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class AdminModerationController {

	private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

	private final SupportService supportService;

	AdminModerationController(SupportService supportService) {
		this.supportService = supportService;
	}

	@GetMapping("/queue")
	ResponseEntity<?> queue(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String type,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			HttpServletRequest request) {
		return ResponseEntity.ok(this.supportService.moderationQueue(adminAccess(jwt, request), type, page, size));
	}

	@PostMapping("/actions")
	ResponseEntity<?> actions(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ModerationActionRequest body,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(this.supportService.applyModerationAction(adminAccess(jwt, request), body));
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
			throw new SupportException(SupportErrorCode.MODERATION_ACCESS_DENIED);
		}
	}

	private static List<String> permissions(Jwt jwt) {
		List<String> values = jwt.getClaimAsStringList("permissions");
		return values == null ? List.of() : values;
	}
}
