package com.motodigo.core.payment.controller;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.payment.entity.PaymentProvider;
import com.motodigo.core.payment.entity.PaymentStatus;
import com.motodigo.core.payment.exception.PaymentErrorCode;
import com.motodigo.core.payment.exception.PaymentException;
import com.motodigo.core.payment.service.PaymentService;
import com.motodigo.core.payment.service.PaymentService.AdminAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments/transactions")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class AdminPaymentController {

	private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final PaymentService paymentService;

	AdminPaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@GetMapping
	ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String provider,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String externalReference,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			HttpServletRequest request) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		return ResponseEntity.ok(this.paymentService.listForAdmin(
				adminAccess(jwt, request),
				parseProvider(provider),
				parseStatus(status),
				externalReference,
				safePage,
				safeSize));
	}

	@GetMapping("/{paymentId}")
	ResponseEntity<?> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId,
			HttpServletRequest request) {
		return ResponseEntity.ok(this.paymentService.getForAdmin(adminAccess(jwt, request), paymentId));
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
			throw new PaymentException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
		}
	}

	private static List<String> permissions(Jwt jwt) {
		List<String> values = jwt.getClaimAsStringList("permissions");
		return values == null ? List.of() : values;
	}

	private static PaymentProvider parseProvider(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return PaymentProvider.valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		}
	}

	private static PaymentStatus parseStatus(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return PaymentStatus.valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		}
	}
}
