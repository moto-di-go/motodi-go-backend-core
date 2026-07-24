package com.motodigo.core.kyc.controller;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.kyc.dto.AdminKycDecisionRequest;
import com.motodigo.core.kyc.dto.DownloadedKycDocument;
import com.motodigo.core.kyc.exception.KycErrorCode;
import com.motodigo.core.kyc.exception.KycException;
import com.motodigo.core.kyc.service.KycService;
import com.motodigo.core.kyc.service.KycService.AdminAccess;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@RequestMapping("/api/v1/admin/kyc")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class AdminKycController {

	private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

	private final KycService kycService;

	AdminKycController(KycService kycService) {
		this.kycService = kycService;
	}

	@GetMapping("/pending")
	ResponseEntity<?> listPending(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
		return ResponseEntity.ok(this.kycService.listPending(adminAccess(jwt, request)));
	}

	@GetMapping("/reviews")
	ResponseEntity<?> listReviews(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) ProfileType profileType,
			@RequestParam(required = false) ValidationStatus status,
			HttpServletRequest request) {
		return ResponseEntity.ok(this.kycService.listReviews(adminAccess(jwt, request), profileType, status));
	}

	@GetMapping("/{profileId}/documents")
	ResponseEntity<?> listProfileDocuments(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID profileId,
			HttpServletRequest request) {
		return ResponseEntity.ok(this.kycService.listDocumentsForAdmin(profileId, adminAccess(jwt, request)));
	}

	@GetMapping("/documents/{documentId}/content")
	ResponseEntity<byte[]> downloadDocument(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId,
			HttpServletRequest request) {
		DownloadedKycDocument document = this.kycService.downloadDocumentForAdmin(documentId, adminAccess(jwt, request));
		return ResponseEntity.ok()
				.contentType(mediaType(document.metadata().mimeType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline()
								.filename(document.metadata().type().name().toLowerCase() + "-" + document.metadata().id())
								.build()
								.toString())
				.body(document.content());
	}

	@PostMapping("/{profileId}/decision")
	ResponseEntity<?> decide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID profileId,
			@Valid @RequestBody AdminKycDecisionRequest body, HttpServletRequest request) {
		return ResponseEntity.ok(this.kycService.decide(profileId, body, adminAccess(jwt, request)));
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
			throw new KycException(KycErrorCode.KYC_ACCESS_DENIED);
		}
	}

	private static List<String> permissions(Jwt jwt) {
		List<String> values = jwt.getClaimAsStringList("permissions");
		return values == null ? List.of() : values;
	}

	private static MediaType mediaType(String value) {
		try {
			return MediaType.parseMediaType(value);
		}
		catch (RuntimeException ex) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}
}
