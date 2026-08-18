package com.motodigo.core.pricing.controller;

import com.motodigo.core.pricing.dto.PricingEstimateRequest;
import com.motodigo.core.pricing.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class PricingController {

	private final PricingService pricingService;

	PricingController(PricingService pricingService) {
		this.pricingService = pricingService;
	}

	@PostMapping("/estimate")
	ResponseEntity<?> estimate(@Valid @RequestBody PricingEstimateRequest request) {
		return ResponseEntity.ok(this.pricingService.estimateFare(request));
	}
}
