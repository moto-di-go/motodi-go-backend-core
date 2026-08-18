package com.motodigo.core.urban.controller;

import com.motodigo.core.urban.service.PlaceSearchFacade;
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

	private final PlaceSearchFacade placeSearchFacade;

	UrbanSearchController(UrbanSearchService urbanSearchService, UrbanPlaceService urbanPlaceService,
			PlaceSearchFacade placeSearchFacade) {
		this.urbanSearchService = urbanSearchService;
		this.urbanPlaceService = urbanPlaceService;
		this.placeSearchFacade = placeSearchFacade;
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

	@GetMapping("/places/suggest")
	ResponseEntity<?> suggestPlaces(
			@RequestParam(name = "q", required = false) String query,
			@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lon,
			@RequestParam(required = false) String kind) {
		return ResponseEntity.ok(this.placeSearchFacade.suggest(query, lat, lon, kind));
	}

	@GetMapping("/geocode")
	ResponseEntity<?> geocode(
			@RequestParam(name = "q", required = false) String query,
			@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lon,
			@RequestParam(required = false) String kind) {
		return ResponseEntity.ok(this.placeSearchFacade.suggest(query, lat, lon, kind));
	}

	@GetMapping("/reverse")
	ResponseEntity<?> reverse(@RequestParam double lat, @RequestParam double lon) {
		return ResponseEntity.ok(this.placeSearchFacade.reverse(lat, lon));
	}
}
