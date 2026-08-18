package com.motodigo.core.pricing.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.pricing.entity.PricingRuleRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PricingRuleRepository {

	private static final String SELECT = """
			SELECT id, zone_code, ST_AsGeoJSON(zone_geom) AS zone_geojson, time_start, time_end,
			       base_fare, price_per_km, night_coefficient, weather_coefficient, active,
			       effective_from, effective_to
			FROM pricing_rules
			""";

	private final JdbcTemplate jdbcTemplate;

	public PricingRuleRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<PricingRuleRecord> findAll() {
		return this.jdbcTemplate.query(SELECT + " ORDER BY created_at DESC", this::map);
	}

	public Optional<PricingRuleRecord> findById(UUID id) {
		return this.jdbcTemplate.query(SELECT + " WHERE id = ?", this::map, id).stream().findFirst();
	}

	public Optional<PricingRuleRecord> findActiveForPoint(double lat, double lon, Instant at) {
		return this.jdbcTemplate.query(SELECT + """
				WHERE active = true
				  AND effective_from <= ?
				  AND (effective_to IS NULL OR effective_to > ?)
				  AND (zone_geom IS NULL OR ST_Covers(zone_geom, ST_SetSRID(ST_MakePoint(?, ?), 4326)))
				ORDER BY ST_Area(zone_geom) ASC NULLS LAST
				LIMIT 1
				""", this::map, at, at, lon, lat).stream().findFirst();
	}

	public PricingRuleRecord insert(String zoneCode, String zoneWkt, LocalTime timeStart, LocalTime timeEnd,
			BigDecimal baseFare, BigDecimal pricePerKm, BigDecimal nightCoefficient, BigDecimal weatherCoefficient,
			boolean active, UUID createdBy) {
		UUID id = this.jdbcTemplate.queryForObject("""
				INSERT INTO pricing_rules (
				  zone_code, zone_geom, time_start, time_end, base_fare, price_per_km,
				  night_coefficient, weather_coefficient, active, created_by_profile_id
				)
				VALUES (?, CASE WHEN ? IS NULL THEN NULL ELSE ST_Multi(ST_GeomFromText(?, 4326)) END,
				        ?, ?, ?, ?, ?, ?, ?, ?)
				RETURNING id
				""", UUID.class, zoneCode, zoneWkt, zoneWkt, timeStart, timeEnd, baseFare, pricePerKm, nightCoefficient,
				weatherCoefficient, active, createdBy);
		return findById(id).orElseThrow();
	}

	public PricingRuleRecord update(UUID id, String zoneCode, String zoneWkt, LocalTime timeStart, LocalTime timeEnd,
			BigDecimal baseFare, BigDecimal pricePerKm, BigDecimal nightCoefficient, BigDecimal weatherCoefficient,
			Boolean active) {
		this.jdbcTemplate.update("""
				UPDATE pricing_rules
				SET zone_code = COALESCE(?, zone_code),
				    zone_geom = CASE WHEN ? IS NULL THEN zone_geom ELSE ST_Multi(ST_GeomFromText(?, 4326)) END,
				    time_start = COALESCE(?, time_start),
				    time_end = COALESCE(?, time_end),
				    base_fare = COALESCE(?, base_fare),
				    price_per_km = COALESCE(?, price_per_km),
				    night_coefficient = COALESCE(?, night_coefficient),
				    weather_coefficient = COALESCE(?, weather_coefficient),
				    active = COALESCE(?, active)
				WHERE id = ?
				""", zoneCode, zoneWkt, zoneWkt, timeStart, timeEnd, baseFare, pricePerKm, nightCoefficient,
				weatherCoefficient, active, id);
		return findById(id).orElseThrow();
	}

	public UUID insertFareEstimate(UUID routeId, UUID ruleId, BigDecimal amount, double distanceKm, int durationMinutes,
			String trace, Instant expiresAt) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO fare_estimates (
				  route_id, pricing_rule_id, amount_fcfa, distance_km, duration_minutes, applied_rules_trace, expires_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				RETURNING id
				""", UUID.class, routeId, ruleId, amount, distanceKm, durationMinutes, trace, expiresAt);
	}

	private PricingRuleRecord map(ResultSet rs, int rowNum) throws SQLException {
		OffsetDateTime effectiveTo = rs.getObject("effective_to", OffsetDateTime.class);
		return new PricingRuleRecord(
				rs.getObject("id", UUID.class),
				rs.getString("zone_code"),
				rs.getString("zone_geojson"),
				rs.getObject("time_start", LocalTime.class),
				rs.getObject("time_end", LocalTime.class),
				rs.getBigDecimal("base_fare"),
				rs.getBigDecimal("price_per_km"),
				rs.getBigDecimal("night_coefficient"),
				rs.getBigDecimal("weather_coefficient"),
				rs.getBoolean("active"),
				rs.getObject("effective_from", OffsetDateTime.class).toInstant(),
				effectiveTo == null ? null : effectiveTo.toInstant());
	}
}
