package com.motodigo.core.geo.controller;

import java.util.ArrayList;
import java.util.List;

import com.motodigo.core.geo.client.ValhallaPath;
import com.motodigo.core.geo.dto.RouteMatchRequest;
import com.motodigo.core.geo.dto.RouteMatchResponse;
import com.motodigo.core.geo.dto.RoutePreviewRequest;
import com.motodigo.core.geo.dto.RoutePreviewResponse;
import com.motodigo.core.geo.exception.GeoErrorCode;
import com.motodigo.core.geo.exception.GeoException;
import com.motodigo.core.geo.service.RoutingFacade;
import com.motodigo.core.geo.service.RoutingFacade.RoutedResult;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urban/route")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class GeoRouteController {

	private final RoutingFacade routingFacade;

	GeoRouteController(RoutingFacade routingFacade) {
		this.routingFacade = routingFacade;
	}

	@PostMapping("/preview")
	ResponseEntity<RoutePreviewResponse> preview(@Valid @RequestBody RoutePreviewRequest request) {
		RoutedResult result = this.routingFacade.preview(request.originLat(), request.originLon(),
				request.destinationLat(), request.destinationLon());
		return ResponseEntity.ok(RoutePreviewResponse.from(result.route(), result.engine()));
	}

	@PostMapping("/alternatives")
	ResponseEntity<RoutePreviewResponse> alternatives(@Valid @RequestBody RoutePreviewRequest request) {
		return preview(request);
	}

	@PostMapping("/match")
	ResponseEntity<RouteMatchResponse> match(@Valid @RequestBody RouteMatchRequest request) {
		List<double[]> points = new ArrayList<>();
		for (RouteMatchRequest.RouteMatchPoint point : request.points()) {
			points.add(new double[] {point.lat(), point.lon()});
		}
		ValhallaPath matched = this.routingFacade.match(points);
		if (matched == null) {
			throw new GeoException(GeoErrorCode.ROUTE_NOT_FOUND);
		}
		return ResponseEntity.ok(new RouteMatchResponse(matched.polylineWkt(), matched.distanceKm(),
				matched.durationMinutes(), RoutingFacade.ENGINE_VALHALLA));
	}
}
