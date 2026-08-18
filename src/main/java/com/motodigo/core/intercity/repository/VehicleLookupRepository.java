package com.motodigo.core.intercity.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.intercity.entity.VehicleRecord;
import com.motodigo.core.kyc.entity.DocumentStatus;
import com.motodigo.core.kyc.entity.DocumentType;
import com.motodigo.core.kyc.entity.OfficialDocumentRecord;
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

	public List<VehicleRecord> findValidatedActiveByOwner(UUID ownerProfileId) {
		return this.jdbcTemplate.query("""
				SELECT id, owner_profile_id, type, capacity, validation_status, active
				FROM vehicles
				WHERE owner_profile_id = ?
				  AND active = true
				  AND validation_status = 'VALIDATED'
				ORDER BY created_at DESC
				""", this::mapVehicle, ownerProfileId);
	}

	public Optional<OfficialDocumentRecord> findAcceptedVehiclePhoto(UUID vehicleId) {
		return this.jdbcTemplate.query("""
				SELECT id, profile_id, vehicle_id, type, status, encrypted_storage_key, file_name, mime_type, size_bytes,
				       rejection_reason, uploaded_at, updated_at
				FROM official_documents
				WHERE vehicle_id = ?
				  AND type = ?
				  AND status = ?
				ORDER BY uploaded_at DESC
				LIMIT 1
				""", this::mapDocument, vehicleId, DocumentType.VEHICLE_PHOTO.name(), DocumentStatus.ACCEPTED.name())
				.stream()
				.findFirst();
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

	private OfficialDocumentRecord mapDocument(ResultSet rs, int rowNum) throws SQLException {
		return new OfficialDocumentRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("profile_id", UUID.class),
				rs.getObject("vehicle_id", UUID.class),
				DocumentType.valueOf(rs.getString("type")),
				DocumentStatus.valueOf(rs.getString("status")),
				rs.getString("encrypted_storage_key"),
				rs.getString("file_name"),
				rs.getString("mime_type"),
				rs.getLong("size_bytes"),
				rs.getString("rejection_reason"),
				toInstant(rs.getObject("uploaded_at")),
				toInstant(rs.getObject("updated_at")));
	}

	private static java.time.Instant toInstant(Object value) {
		if (value instanceof java.time.OffsetDateTime odt) {
			return odt.toInstant();
		}
		if (value instanceof java.sql.Timestamp ts) {
			return ts.toInstant();
		}
		if (value instanceof java.time.Instant instant) {
			return instant;
		}
		throw new IllegalArgumentException("Unsupported timestamp type: " + value);
	}
}
