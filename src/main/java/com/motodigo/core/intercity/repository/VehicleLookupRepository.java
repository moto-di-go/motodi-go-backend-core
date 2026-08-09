package com.motodigo.core.intercity.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.intercity.entity.VehicleRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class VehicleLookupRepository {

	private final JdbcTemplate jdbcTemplate;

	public VehicleLookupRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<VehicleRecord> findById(UUID vehicleId) {
		return this.jdbcTemplate.query("""
				SELECT id, owner_profile_id, type, capacity, validation_status, active
				FROM vehicles
				WHERE id = ?
				""", this::mapVehicle, vehicleId).stream().findFirst();
	}

	private VehicleRecord mapVehicle(ResultSet rs, int rowNum) throws SQLException {
		return new VehicleRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("owner_profile_id", UUID.class),
				rs.getString("type"),
				rs.getInt("capacity"),
				rs.getString("validation_status"),
				rs.getBoolean("active"));
	}
}
