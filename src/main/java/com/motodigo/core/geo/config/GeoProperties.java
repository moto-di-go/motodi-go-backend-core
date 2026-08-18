package com.motodigo.core.geo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "motodigo.geo")
public record GeoProperties(
		int minIngestIntervalSeconds,
		int targetIngestIntervalSeconds,
		int lastPositionFreshnessSeconds,
		int historyRetentionHours,
		double nearbyDefaultRadiusKm,
		double nearbyMaxRadiusKm,
		double yaoundeMinLat,
		double yaoundeMaxLat,
		double yaoundeMinLon,
		double yaoundeMaxLon,
		String routesYaoundePath) {

	public GeoProperties {
		if (minIngestIntervalSeconds <= 0) {
			minIngestIntervalSeconds = 3;
		}
		if (targetIngestIntervalSeconds <= 0) {
			targetIngestIntervalSeconds = 5;
		}
		if (lastPositionFreshnessSeconds <= 0) {
			lastPositionFreshnessSeconds = 60;
		}
		if (historyRetentionHours <= 0) {
			historyRetentionHours = 24;
		}
		if (nearbyDefaultRadiusKm <= 0) {
			nearbyDefaultRadiusKm = 2;
		}
		if (nearbyMaxRadiusKm <= 0) {
			nearbyMaxRadiusKm = 10;
		}
		if (yaoundeMinLat == 0 && yaoundeMaxLat == 0) {
			yaoundeMinLat = 3.72;
			yaoundeMaxLat = 4.05;
			yaoundeMinLon = 11.38;
			yaoundeMaxLon = 11.64;
		}
		if (routesYaoundePath == null || routesYaoundePath.isBlank()) {
			routesYaoundePath = "./data/gis/routes_yaounde.gpkg";
		}
	}

	public boolean isInsideYaounde(double lat, double lon) {
		return lat >= this.yaoundeMinLat && lat <= this.yaoundeMaxLat
				&& lon >= this.yaoundeMinLon && lon <= this.yaoundeMaxLon;
	}
}
