package com.motodigo.core.geo.service;

import java.nio.file.Files;
import java.nio.file.Path;

import com.motodigo.core.geo.config.GeoProperties;
import com.motodigo.core.geo.repository.RoadNetworkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class YaoundeRoutesImporter implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(YaoundeRoutesImporter.class);

	private final GeoProperties properties;
	private final RoadNetworkRepository roadNetworkRepository;

	public YaoundeRoutesImporter(GeoProperties properties, RoadNetworkRepository roadNetworkRepository) {
		this.properties = properties;
		this.roadNetworkRepository = roadNetworkRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		Path gpkg = Path.of(this.properties.routesYaoundePath());
		if (Files.exists(gpkg)) {
			log.info("routes_yaounde.gpkg found at {}. Seeded road_segments remain the routing fallback; import via ogr2ogr if a full graph refresh is needed.",
					gpkg.toAbsolutePath());
		}
		else {
			log.info("GIS file {} is absent; using seeded Yaounde road grid.", gpkg);
		}
		try {
			this.roadNetworkRepository.rebuildTopology();
		}
		catch (RuntimeException ex) {
			log.warn("Unable to rebuild Yaounde road topology: {}", ex.getMessage());
		}
	}
}
