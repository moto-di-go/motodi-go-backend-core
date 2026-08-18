package com.motodigo.core.geo.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class RoadNetworkRepository {

	private final JdbcTemplate jdbcTemplate;

	public RoadNetworkRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void rebuildTopology() {
		this.jdbcTemplate.update("DELETE FROM road_edges");
		this.jdbcTemplate.update("DELETE FROM road_vertices");
		this.jdbcTemplate.update("""
				INSERT INTO road_vertices (geom)
				SELECT DISTINCT ST_SnapToGrid(ST_StartPoint(d.geom), 0.000001)
				FROM road_segments rs
				CROSS JOIN LATERAL ST_Dump(rs.geom) AS d
				UNION
				SELECT DISTINCT ST_SnapToGrid(ST_EndPoint(d.geom), 0.000001)
				FROM road_segments rs
				CROSS JOIN LATERAL ST_Dump(rs.geom) AS d
				""");
		this.jdbcTemplate.update("""
				INSERT INTO road_edges (source_vertex_id, target_vertex_id, road_segment_id, geom, length_m, max_speed_kmh)
				SELECT
				  sv.id,
				  tv.id,
				  rs.id,
				  line.geom,
				  ST_Length(line.geom::geography),
				  rs.max_speed_kmh
				FROM road_segments rs
				CROSS JOIN LATERAL ST_Dump(rs.geom) dump
				CROSS JOIN LATERAL (SELECT dump.geom AS geom) line
				JOIN road_vertices sv ON ST_Equals(sv.geom, ST_SnapToGrid(ST_StartPoint(line.geom), 0.000001))
				JOIN road_vertices tv ON ST_Equals(tv.geom, ST_SnapToGrid(ST_EndPoint(line.geom), 0.000001))
				WHERE ST_Length(line.geom::geography) > 0
				""");
	}

	public Long nearestVertex(double lat, double lon) {
		return this.jdbcTemplate.query("""
				SELECT id
				FROM road_vertices
				ORDER BY geom <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)
				LIMIT 1
				""", (rs, rowNum) -> rs.getLong("id"), lon, lat)
				.stream()
				.findFirst()
				.orElse(null);
	}

	public List<RoadEdge> loadEdges() {
		return this.jdbcTemplate.query("""
				SELECT id, source_vertex_id, target_vertex_id, ST_AsText(geom) AS wkt, length_m, max_speed_kmh
				FROM road_edges
				""", (rs, rowNum) -> new RoadEdge(
				rs.getLong("id"),
				rs.getLong("source_vertex_id"),
				rs.getLong("target_vertex_id"),
				rs.getString("wkt"),
				rs.getDouble("length_m"),
				rs.getObject("max_speed_kmh") == null ? 40 : rs.getInt("max_speed_kmh")));
	}

	public UUID insertRoute(double originLat, double originLon, double destLat, double destLon, String polylineWkt,
			double distanceKm, int durationMinutes, String status) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO routes (origin_geom, destination_geom, polyline, distance_km, duration_minutes, status)
				VALUES (
				  ST_SetSRID(ST_MakePoint(?, ?), 4326),
				  ST_SetSRID(ST_MakePoint(?, ?), 4326),
				  ST_GeomFromText(?, 4326),
				  ?, ?, ?
				)
				RETURNING id
				""", UUID.class, originLon, originLat, destLon, destLat, polylineWkt, distanceKm, durationMinutes, status);
	}

	public void insertAlternative(UUID routeId, String reason, String polylineWkt, double extraDistanceKm,
			int extraDurationMinutes) {
		this.jdbcTemplate.update("""
				INSERT INTO route_alternatives (route_id, reason, polyline, extra_distance_km, extra_duration_minutes)
				VALUES (?, ?, ST_GeomFromText(?, 4326), ?, ?)
				""", routeId, reason, polylineWkt, extraDistanceKm, extraDurationMinutes);
	}

	public List<BlockingLayer> findBlockingLayers(String polylineWkt) {
		return this.jdbcTemplate.query("""
				SELECT id, type, label, severity, ST_AsText(geom) AS wkt
				FROM traffic_layers
				WHERE active = true
				  AND (valid_to IS NULL OR valid_to > now())
				  AND valid_from <= now()
				  AND type IN ('CONGESTION', 'ROADWORK', 'DAMAGED_ROAD')
				  AND ST_Intersects(geom, ST_GeomFromText(?, 4326))
				""", (rs, rowNum) -> new BlockingLayer(
				rs.getObject("id", UUID.class),
				rs.getString("type"),
				rs.getString("label"),
				rs.getString("severity"),
				rs.getString("wkt")), polylineWkt);
	}

	public List<Long> findDegradedEdgeIds() {
		return this.jdbcTemplate.query("""
				SELECT DISTINCT e.id
				FROM road_edges e
				JOIN traffic_layers t ON ST_Intersects(e.geom, t.geom)
				WHERE t.active = true
				  AND t.valid_from <= now()
				  AND (t.valid_to IS NULL OR t.valid_to > now())
				  AND t.type IN ('DAMAGED_ROAD', 'ROADWORK')
				""", (rs, rowNum) -> rs.getLong("id"));
	}

	public record RoadEdge(long id, long source, long target, String wkt, double lengthM, int maxSpeedKmh) {
	}

	public record BlockingLayer(UUID id, String type, String label, String severity, String wkt) {
	}
}
