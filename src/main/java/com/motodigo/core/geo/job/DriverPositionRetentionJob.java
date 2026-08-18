package com.motodigo.core.geo.job;

import com.motodigo.core.geo.service.GeoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class DriverPositionRetentionJob {

	private static final Logger log = LoggerFactory.getLogger(DriverPositionRetentionJob.class);

	private final GeoService geoService;

	public DriverPositionRetentionJob(GeoService geoService) {
		this.geoService = geoService;
	}

	@Scheduled(cron = "${motodigo.geo.retention-cron:0 15 * * * *}")
	public void purgeExpiredPositions() {
		int deleted = this.geoService.purgeExpiredPositions();
		if (deleted > 0) {
			log.info("Purged {} GPS position(s) older than retention window", deleted);
		}
	}
}
