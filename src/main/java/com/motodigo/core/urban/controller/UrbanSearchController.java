package com.motodigo.core.urban.controller;

import com.motodigo.core.urban.service.UrbanPlaceService;
import com.motodigo.core.urban.service.UrbanSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urban")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class UrbanSearchController {

	private final UrbanSearchService urbanSearchService;
	private final UrbanPlaceService urbanPlaceService;

	UrbanSearchController(UrbanSearchService urbanSearchService, UrbanPlaceService urbanPlaceService) {
		this.urbanSearchService = urbanSearchService;
		this.urbanPlaceService = urbanPlaceService;
	}

	@GetMapping("/drivers/nearby")
	ResponseEntity<?> nearby(
			@RequestParam double lat,
			@RequestParam double lon,
			@RequestParam(required = false) Double radiusKm,
			@RequestParam(required = false) Double destLat,
			@RequestParam(required = false) Double destLon) {
		return ResponseEntity.ok(this.urbanSearchService.findNearby(lat, lon, radiusKm, destLat, destLon));
	}

	@GetMapping("/places/popular")
	ResponseEntity<?> popularPlaces() {
		return ResponseEntity.ok(this.urbanPlaceService.popular());
	}

	@GetMapping("/geocode")
	ResponseEntity<?> geocode(@RequestParam(name = "q", required = false) String query) {
		return ResponseEntity.ok(this.urbanPlaceService.geocode(query));
	}

	@GetMapping("/reverse")
	ResponseEntity<?> reverse(@RequestParam double lat, @RequestParam double lon) {
		return ResponseEntity.ok(this.urbanPlaceService.reverse(lat, lon));
	}
}
