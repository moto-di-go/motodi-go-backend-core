package com.motodigo.core.urban.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.urban.entity.UrbanPlaceRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class UrbanPlaceRepository {

	private static final String SELECT = """
			SELECT id, label, ST_Y(geom) AS lat, ST_X(geom) AS lon, kind, in_service_area, sort_order
			FROM urban_places
			""";

	private final JdbcTemplate jdbcTemplate;

	public UrbanPlaceRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<UrbanPlaceRecord> findPopular() {
		return this.jdbcTemplate.query(SELECT + """
				WHERE in_service_area = true
				  AND kind = 'NEIGHBORHOOD'
				ORDER BY sort_order ASC, label ASC
				""", this::map);
	}

	public List<UrbanPlaceRecord> findAllInServiceArea() {
		return this.jdbcTemplate.query(SELECT + """
				WHERE in_service_area = true
				ORDER BY sort_order ASC, label ASC
				""", this::map);
	}

	public List<UrbanPlaceRecord> findByKind(String kind) {
		return this.jdbcTemplate.query(SELECT + """
				WHERE kind = ?
				ORDER BY sort_order ASC, label ASC
				""", this::map, kind);
	}

	public List<UrbanPlaceRecord> findNear(double lat, double lon, double radiusKm) {
		double meters = Math.max(1_000d, radiusKm * 1_000d);
		return this.jdbcTemplate.query(SELECT + """
				WHERE ST_DWithin(
				        geom::geography,
				        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
				        ?
				      )
				ORDER BY geom <-> ST_SetSRID(ST_MakePoint(?, ?), 4326), sort_order ASC, label ASC
				""", this::map, lon, lat, meters, lon, lat);
	}

	private UrbanPlaceRecord map(ResultSet rs, int rowNum) throws SQLException {
		return new UrbanPlaceRecord(
				rs.getObject("id", UUID.class),
				rs.getString("label"),
				rs.getDouble("lat"),
				rs.getDouble("lon"),
				rs.getString("kind"),
				rs.getBoolean("in_service_area"),
				rs.getInt("sort_order"));
	}
}
