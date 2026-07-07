package com.motodigo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class CoreDataMigrationSmokeTest {

	private static final String TEST_SCHEMA = "core_data_001_it";

	@Test
	@EnabledIfEnvironmentVariable(named = "MOTODIGO_DB_INTEGRATION_TESTS", matches = "true")
	void migrationsSupportYaoundePostgisSmokeTest() throws Exception {
		String url = envOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/motodigo");
		String username = envOrDefault("SPRING_DATASOURCE_USERNAME", "motodigo");
		String password = envOrDefault("SPRING_DATASOURCE_PASSWORD", "change-me-local-only");

		Flyway flyway = Flyway.configure()
				.dataSource(url, username, password)
				.locations("classpath:db/migration")
				.schemas(TEST_SCHEMA)
				.defaultSchema(TEST_SCHEMA)
				.createSchemas(true)
				.cleanDisabled(false)
				.load();

		flyway.clean();
		flyway.migrate();

		try (Connection connection = DriverManager.getConnection(url, username, password);
				Statement statement = connection.createStatement()) {
			statement.execute("SET search_path TO " + TEST_SCHEMA + ", public");

			statement.execute("""
					CREATE TABLE IF NOT EXISTS spatial_smoke_test (
					  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
					  label text NOT NULL UNIQUE,
					  geom geometry(Point, 4326) NOT NULL
					)
					""");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_spatial_smoke_test_geom ON spatial_smoke_test USING gist (geom)");
			statement.execute("""
					INSERT INTO spatial_smoke_test (label, geom)
					VALUES ('Yaounde', ST_SetSRID(ST_MakePoint(11.52, 3.87), 4326))
					ON CONFLICT (label) DO UPDATE SET geom = EXCLUDED.geom
					""");

			try (ResultSet resultSet = statement.executeQuery("""
					SELECT count(*) AS nearby_count
					FROM spatial_smoke_test
					WHERE ST_DWithin(
					  geom::geography,
					  ST_SetSRID(ST_MakePoint(11.52, 3.87), 4326)::geography,
					  1000
					)
					""")) {
				assertTrue(resultSet.next());
				assertEquals(1, resultSet.getInt("nearby_count"));
			}

			statement.execute("""
					INSERT INTO road_segments (source_layer, source_feature_id, name, road_class, geom)
					VALUES (
					  'it-smoke',
					  'yaounde-road-001',
					  'Yaounde smoke route',
					  'primary',
					  ST_Multi(ST_GeomFromText('LINESTRING(11.52 3.87, 11.521 3.871)', 4326))::geometry(MultiLineString, 4326)
					)
					ON CONFLICT (source_name, source_layer, source_feature_id) WHERE source_layer IS NOT NULL AND source_feature_id IS NOT NULL
					DO UPDATE SET geom = EXCLUDED.geom
					""");

			try (ResultSet resultSet = statement.executeQuery("""
					SELECT ST_SRID(geom) AS srid, ST_IsValid(geom) AS valid
					FROM road_segments
					WHERE source_layer = 'it-smoke'
					""")) {
				assertTrue(resultSet.next());
				assertEquals(4326, resultSet.getInt("srid"));
				assertTrue(resultSet.getBoolean("valid"));
			}

			try (ResultSet resultSet = statement.executeQuery("""
					SELECT count(*) AS gist_count
					FROM pg_indexes
					WHERE schemaname = 'core_data_001_it'
					  AND tablename = 'road_segments'
					  AND indexdef ILIKE '%gist%'
					""")) {
				assertTrue(resultSet.next());
				assertTrue(resultSet.getInt("gist_count") >= 1);
			}
		}
	}

	private static String envOrDefault(String name, String defaultValue) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? defaultValue : value;
	}
}
