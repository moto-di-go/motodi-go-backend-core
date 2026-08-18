package com.motodigo.core.pricing.controller;

import com.motodigo.core.pricing.service.AdminPricingGisService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gis")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class GisLayerController {

	private final AdminPricingGisService adminPricingGisService;

	GisLayerController(AdminPricingGisService adminPricingGisService) {
		this.adminPricingGisService = adminPricingGisService;
	}

	@GetMapping("/layers")
	ResponseEntity<?> activeLayers() {
		return ResponseEntity.ok(this.adminPricingGisService.listLayers(true));
	}
}
