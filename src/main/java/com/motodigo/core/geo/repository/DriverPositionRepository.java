package com.motodigo.core.geo.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.geo.entity.DriverPositionRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class DriverPositionRepository {

	private static final String SELECT_POSITION = """
			SELECT id, driver_profile_id,
			       ST_Y(geom::geometry) AS lat,
			       ST_X(geom::geometry) AS lon,
			       speed_kmh, accuracy_meters, captured_at, received_at, consent_confirmed
			""";

	private final JdbcTemplate jdbcTemplate;

	public DriverPositionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public DriverPositionRecord insert(UUID driverProfileId, double lat, double lon, Double speedKmh,
			Double accuracyMeters, Instant capturedAt, boolean consentConfirmed) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO driver_positions (
				  driver_profile_id, geom, speed_kmh, accuracy_meters, captured_at, consent_confirmed
				)
				VALUES (?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?, ?)
				RETURNING id, driver_profile_id,
				          ST_Y(geom::geometry) AS lat,
				          ST_X(geom::geometry) AS lon,
				          speed_kmh, accuracy_meters, captured_at, received_at, consent_confirmed
				""", this::mapPosition, driverProfileId, lon, lat, speedKmh, accuracyMeters, capturedAt, consentConfirmed);
	}

	public void upsertLastPosition(DriverPositionRecord position) {
		this.jdbcTemplate.update("""
				INSERT INTO driver_last_positions (
				  driver_profile_id, position_id, geom, speed_kmh, accuracy_meters,
				  captured_at, received_at, consent_confirmed
				)
				VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?, ?, ?)
				ON CONFLICT (driver_profile_id) DO UPDATE SET
				  position_id = EXCLUDED.position_id,
				  geom = EXCLUDED.geom,
				  speed_kmh = EXCLUDED.speed_kmh,
				  accuracy_meters = EXCLUDED.accuracy_meters,
				  captured_at = EXCLUDED.captured_at,
				  received_at = EXCLUDED.received_at,
				  consent_confirmed = EXCLUDED.consent_confirmed
				""", position.driverProfileId(), position.id(), position.lon(), position.lat(), position.speedKmh(),
				position.accuracyMeters(), position.capturedAt(), position.receivedAt(), position.consentConfirmed());
	}

	public Optional<DriverPositionRecord> findLastByDriver(UUID driverProfileId) {
		return this.jdbcTemplate.query(SELECT_POSITION + """
				FROM driver_last_positions
				WHERE driver_profile_id = ?
				""", this::mapLastPosition, driverProfileId).stream().findFirst();
	}

	public Optional<Instant> findLastCapturedAt(UUID driverProfileId) {
		return this.jdbcTemplate.query("""
				SELECT captured_at
				FROM driver_last_positions
				WHERE driver_profile_id = ?
				""", (rs, rowNum) -> rs.getObject("captured_at", OffsetDateTime.class).toInstant(), driverProfileId)
				.stream()
				.findFirst();
	}

	public List<DriverPositionRecord> findNearbyAvailable(double lat, double lon, double radiusKm, Instant minCapturedAt) {
		return this.jdbcTemplate.query("""
				SELECT lp.position_id AS id,
				       lp.driver_profile_id,
				       ST_Y(lp.geom::geometry) AS lat,
				       ST_X(lp.geom::geometry) AS lon,
				       lp.speed_kmh,
				       lp.accuracy_meters,
				       lp.captured_at,
				       lp.received_at,
				       lp.consent_confirmed,
				       ST_DistanceSphere(lp.geom, ST_SetSRID(ST_MakePoint(?, ?), 4326)) AS approach_meters
				FROM driver_last_positions lp
				JOIN profiles p ON p.id = lp.driver_profile_id
				JOIN driver_profiles dp ON dp.profile_id = lp.driver_profile_id
				WHERE p.type = 'MOTOTAXI'
				  AND p.validation_status = 'VALIDATED'
				  AND dp.availability = 'AVAILABLE'
				  AND dp.gps_consent = true
				  AND lp.consent_confirmed = true
				  AND lp.captured_at >= ?
				  AND ST_DWithin(lp.geom::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
				ORDER BY approach_meters ASC
				""", this::mapPosition, lon, lat, minCapturedAt, lon, lat, radiusKm * 1000);
	}

	public int deleteOlderThan(Instant cutoff) {
		this.jdbcTemplate.update("""
				DELETE FROM driver_last_positions
				WHERE captured_at < ?
				""", cutoff);
		return this.jdbcTemplate.update("""
				DELETE FROM driver_positions
				WHERE captured_at < ?
				""", cutoff);
	}

	private DriverPositionRecord mapLastPosition(ResultSet rs, int rowNum) throws SQLException {
		return mapPosition(rs, rowNum);
	}

	private DriverPositionRecord mapPosition(ResultSet rs, int rowNum) throws SQLException {
		return new DriverPositionRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("driver_profile_id", UUID.class),
				rs.getDouble("lat"),
				rs.getDouble("lon"),
				rs.getObject("speed_kmh") == null ? null : rs.getDouble("speed_kmh"),
				rs.getObject("accuracy_meters") == null ? null : rs.getDouble("accuracy_meters"),
				rs.getObject("captured_at", OffsetDateTime.class).toInstant(),
				rs.getObject("received_at", OffsetDateTime.class).toInstant(),
				rs.getBoolean("consent_confirmed"));
	}
}
