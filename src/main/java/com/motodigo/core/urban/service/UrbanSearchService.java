package com.motodigo.core.urban.service;

import java.util.ArrayList;
import java.util.List;

import com.motodigo.core.geo.config.GeoProperties;
import com.motodigo.core.geo.entity.DriverPositionRecord;
import com.motodigo.core.geo.service.GeoService;
import com.motodigo.core.pricing.dto.PricingEstimateRequest;
import com.motodigo.core.pricing.dto.PricingEstimateResponse;
import com.motodigo.core.pricing.service.PricingService;
import com.motodigo.core.urban.dto.NearbyDriverResponse;
import com.motodigo.core.urban.dto.NearbyDriversPageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class UrbanSearchService {

	private final GeoService geoService;
	private final PricingService pricingService;
	private final GeoProperties geoProperties;

	public UrbanSearchService(GeoService geoService, PricingService pricingService, GeoProperties geoProperties) {
		this.geoService = geoService;
		this.pricingService = pricingService;
		this.geoProperties = geoProperties;
	}

	@Transactional
	public NearbyDriversPageResponse findNearby(double lat, double lon, Double radiusKm, Double destLat, Double destLon) {
		List<DriverPositionRecord> drivers = this.geoService.findNearbyAvailable(lat, lon, radiusKm);
		double usedRadius = radiusKm == null ? this.geoProperties.nearbyDefaultRadiusKm() : radiusKm;
		if (drivers.isEmpty()) {
			return new NearbyDriversPageResponse(List.of(),
					"Aucun mototaxi valide et disponible dans un rayon de " + usedRadius + " km. Elargissez le rayon.",
					usedRadius);
		}
		PricingEstimateResponse estimate = null;
		if (destLat != null && destLon != null) {
			estimate = this.pricingService.estimateFare(new PricingEstimateRequest(lat, lon, destLat, destLon, "URBAN"));
		}
		List<NearbyDriverResponse> items = new ArrayList<>();
		for (DriverPositionRecord driver : drivers) {
			double approachKm = haversineKm(lat, lon, driver.lat(), driver.lon());
			items.add(new NearbyDriverResponse(
					driver.driverProfileId(),
					driver.lat(),
					driver.lon(),
					Math.round(approachKm * 1000d) / 1000d,
					"AVAILABLE",
					estimate == null ? null : estimate.amountFcfa(),
					estimate == null ? 0 : estimate.distanceKm(),
					estimate == null ? 0 : estimate.durationMinutes(),
					estimate == null ? null : estimate.appliedRulesTrace(),
					driver.capturedAt()));
		}
		return new NearbyDriversPageResponse(List.copyOf(items), null, usedRadius);
	}

	static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
		double earth = 6371;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return earth * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}
}
