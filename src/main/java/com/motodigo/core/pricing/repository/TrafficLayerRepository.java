package com.motodigo.core.pricing.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.pricing.dto.TrafficLayerResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class TrafficLayerRepository {

	private static final String SELECT = """
			SELECT id, type, label, ST_AsText(geom) AS wkt, ST_AsGeoJSON(geom) AS geojson,
			       severity, valid_from, valid_to, active, source
			FROM traffic_layers
			""";

	private final JdbcTemplate jdbcTemplate;

	public TrafficLayerRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<TrafficLayerResponse> findAll() {
		return this.jdbcTemplate.query(SELECT + " ORDER BY created_at DESC", this::map);
	}

	public List<TrafficLayerResponse> findActive() {
		return this.jdbcTemplate.query(SELECT + """
				WHERE active = true
				  AND valid_from <= now()
				  AND (valid_to IS NULL OR valid_to > now())
				ORDER BY severity DESC, created_at DESC
				""", this::map);
	}

	public Optional<TrafficLayerResponse> findById(UUID id) {
		return this.jdbcTemplate.query(SELECT + " WHERE id = ?", this::map, id).stream().findFirst();
	}

	public TrafficLayerResponse insert(String type, String label, String wkt, String severity, Instant validFrom,
			Instant validTo, boolean active, String source, UUID createdBy) {
		UUID id = this.jdbcTemplate.queryForObject("""
				INSERT INTO traffic_layers (type, label, geom, severity, valid_from, valid_to, active, source, created_by_profile_id)
				VALUES (?, ?, ST_GeomFromText(?, 4326), ?, COALESCE(?, now()), ?, ?, ?, ?)
				RETURNING id
				""", UUID.class, type, label, wkt, severity, validFrom, validTo, active, source, createdBy);
		return findById(id).orElseThrow();
	}

	public TrafficLayerResponse update(UUID id, String type, String label, String wkt, String severity, Instant validFrom,
			Instant validTo, Boolean active, String source) {
		this.jdbcTemplate.update("""
				UPDATE traffic_layers
				SET type = COALESCE(?, type),
				    label = COALESCE(?, label),
				    geom = CASE WHEN ? IS NULL THEN geom ELSE ST_GeomFromText(?, 4326) END,
				    severity = COALESCE(?, severity),
				    valid_from = COALESCE(?, valid_from),
				    valid_to = ?,
				    active = COALESCE(?, active),
				    source = COALESCE(?, source)
				WHERE id = ?
				""", type, label, wkt, wkt, severity, validFrom, validTo, active, source, id);
		return findById(id).orElseThrow();
	}

	private TrafficLayerResponse map(ResultSet rs, int rowNum) throws SQLException {
		OffsetDateTime validTo = rs.getObject("valid_to", OffsetDateTime.class);
		Instant validFrom = rs.getObject("valid_from", OffsetDateTime.class).toInstant();
		Instant validToInstant = validTo == null ? null : validTo.toInstant();
		boolean active = rs.getBoolean("active");
		Instant now = Instant.now();
		boolean fresh = active && !validFrom.isAfter(now) && (validToInstant == null || validToInstant.isAfter(now));
		return new TrafficLayerResponse(
				rs.getObject("id", UUID.class),
				rs.getString("type"),
				rs.getString("label"),
				rs.getString("wkt"),
				rs.getString("geojson"),
				rs.getString("severity"),
				validFrom,
				validToInstant,
				active,
				rs.getString("source"),
				validFrom,
				fresh);
	}
}
