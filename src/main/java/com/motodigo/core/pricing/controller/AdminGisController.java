package com.motodigo.core.pricing.controller;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.pricing.dto.TrafficLayerRequest;
import com.motodigo.core.pricing.exception.PricingErrorCode;
import com.motodigo.core.pricing.exception.PricingException;
import com.motodigo.core.pricing.service.AdminPricingGisService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/gis/layers")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class AdminGisController {

	private final AdminPricingGisService adminPricingGisService;

	AdminGisController(AdminPricingGisService adminPricingGisService) {
		this.adminPricingGisService = adminPricingGisService;
	}

	@GetMapping
	ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt) {
		requirePermission(jwt, "GIS_READ");
		return ResponseEntity.ok(this.adminPricingGisService.listLayers(false));
	}

	@PostMapping
	ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TrafficLayerRequest request) {
		requirePermission(jwt, "GIS_WRITE");
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(this.adminPricingGisService.createLayer(request, activeProfileId(jwt)));
	}

	@PutMapping("/{id}")
	ResponseEntity<?> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody TrafficLayerRequest request) {
		requirePermission(jwt, "GIS_WRITE");
		return ResponseEntity.ok(this.adminPricingGisService.updateLayer(id, request, activeProfileId(jwt)));
	}

	private static void requirePermission(Jwt jwt, String permission) {
		List<String> permissions = jwt.getClaimAsStringList("permissions");
		if (permissions == null || !permissions.contains(permission)) {
			throw new PricingException(PricingErrorCode.ADMIN_REQUIRED);
		}
	}

	private static UUID activeProfileId(Jwt jwt) {
		String value = jwt.getClaimAsString("activeProfileId");
		return value == null || value.isBlank() ? null : UUID.fromString(value);
	}
}
