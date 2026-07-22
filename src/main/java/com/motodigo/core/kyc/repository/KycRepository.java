package com.motodigo.core.kyc.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.kyc.entity.AccessAction;
import com.motodigo.core.kyc.entity.DocumentStatus;
import com.motodigo.core.kyc.entity.DocumentType;
import com.motodigo.core.kyc.entity.KycProfileRecord;
import com.motodigo.core.kyc.entity.OfficialDocumentRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class KycRepository {

	private final JdbcTemplate jdbcTemplate;

	public KycRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<KycProfileRecord> findProfileById(UUID profileId) {
		return this.jdbcTemplate.query("""
				SELECT id, user_id, type, validation_status, display_name
				FROM profiles
				WHERE id = ?
				""", this::mapProfile, profileId).stream().findFirst();
	}

	public boolean vehicleBelongsToProfile(UUID profileId, UUID vehicleId) {
		Boolean exists = this.jdbcTemplate.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM vehicles WHERE id = ? AND owner_profile_id = ?)
				""", Boolean.class, vehicleId, profileId);
		return Boolean.TRUE.equals(exists);
	}

	public List<OfficialDocumentRecord> findDocumentsByProfile(UUID profileId) {
		return this.jdbcTemplate.query("""
				SELECT *
				FROM official_documents
				WHERE profile_id = ?
				ORDER BY uploaded_at DESC
				""", this::mapDocument, profileId);
	}

	public Optional<OfficialDocumentRecord> findDocumentById(UUID documentId) {
		return this.jdbcTemplate.query("""
				SELECT *
				FROM official_documents
				WHERE id = ?
				""", this::mapDocument, documentId).stream().findFirst();
	}

	public OfficialDocumentRecord saveDocument(UUID profileId, UUID vehicleId, DocumentType type, String encryptedStorageKey,
			String mimeType, long sizeBytes, String checksum) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO official_documents (
				  profile_id, vehicle_id, type, status, encrypted_storage_key, mime_type, size_bytes, checksum
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				RETURNING *
				""", this::mapDocument, profileId, vehicleId, type.name(), DocumentStatus.PENDING_REVIEW.name(),
				encryptedStorageKey, mimeType, sizeBytes, checksum);
	}

	public void markProfilePending(UUID profileId) {
		this.jdbcTemplate.update("""
				UPDATE profiles
				SET validation_status = 'PENDING', updated_at = now()
				WHERE id = ? AND validation_status IN ('DRAFT', 'REJECTED')
				""", profileId);
	}

	public void saveAudit(UUID documentId, UUID actorProfileId, AccessAction action, String actorRole, String correlationId) {
		this.jdbcTemplate.update("""
				INSERT INTO document_access_audits (document_id, actor_profile_id, action, actor_role, correlation_id)
				VALUES (?, ?, ?, ?, ?)
				""", documentId, actorProfileId, action.name(), actorRole, blankToNull(correlationId));
	}

	public int auditCount(UUID documentId, AccessAction action) {
		Integer count = this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM document_access_audits WHERE document_id = ? AND action = ?
				""", Integer.class, documentId, action.name());
		return count == null ? 0 : count;
	}

	private KycProfileRecord mapProfile(ResultSet rs, int rowNum) throws SQLException {
		return new KycProfileRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				ProfileType.valueOf(rs.getString("type")),
				ValidationStatus.valueOf(rs.getString("validation_status")),
				rs.getString("display_name"));
	}

	private OfficialDocumentRecord mapDocument(ResultSet rs, int rowNum) throws SQLException {
		return new OfficialDocumentRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("profile_id", UUID.class),
				rs.getObject("vehicle_id", UUID.class),
				DocumentType.valueOf(rs.getString("type")),
				DocumentStatus.valueOf(rs.getString("status")),
				rs.getString("encrypted_storage_key"),
				rs.getString("mime_type"),
				rs.getLong("size_bytes"),
				rs.getString("checksum"),
				rs.getString("rejection_reason"),
				instant(rs, "uploaded_at"),
				instant(rs, "updated_at"));
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
