package com.motodigo.core.kyc.controller;

import java.util.UUID;

import com.motodigo.core.kyc.entity.DocumentType;
import com.motodigo.core.kyc.exception.KycErrorCode;
import com.motodigo.core.kyc.exception.KycException;
import com.motodigo.core.kyc.service.KycService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/kyc")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class KycController {

	private final KycService kycService;

	KycController(KycService kycService) {
		this.kycService = kycService;
	}

	@GetMapping("/documents")
	ResponseEntity<?> listCurrentDocuments(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(this.kycService.listCurrentDocuments(userId(jwt), activeProfileId(jwt)));
	}

	@PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<?> uploadDocument(@AuthenticationPrincipal Jwt jwt, @RequestParam DocumentType type,
			@RequestParam(required = false) UUID vehicleId, @RequestPart MultipartFile file) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(this.kycService.uploadDocument(userId(jwt), activeProfileId(jwt), type, vehicleId, file));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
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
}
