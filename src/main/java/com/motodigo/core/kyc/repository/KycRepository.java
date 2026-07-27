package com.motodigo.core.kyc.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.kyc.dto.AdminKycQueueItemResponse;
import com.motodigo.core.kyc.entity.AccessAction;
import com.motodigo.core.kyc.entity.DocumentDecisionRecord;
import com.motodigo.core.kyc.entity.DocumentStatus;
import com.motodigo.core.kyc.entity.DocumentType;
import com.motodigo.core.kyc.entity.KycDecision;
import com.motodigo.core.kyc.entity.KycProfileRecord;
import com.motodigo.core.kyc.entity.OfficialDocumentRecord;
import com.motodigo.core.kyc.entity.ValidationDecisionRecord;
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

	public List<AdminKycQueueItemResponse> findPendingQueue() {
		return findReviewQueue(null, ValidationStatus.PENDING);
	}

	public List<AdminKycQueueItemResponse> findReviewQueue(ProfileType profileType, ValidationStatus status) {
		List<Object> params = new ArrayList<>();
		StringBuilder sql = new StringBuilder("""
				SELECT p.id AS profile_id,
				       p.type AS profile_type,
				       p.validation_status,
				       p.display_name,
				       count(d.id) AS documents_count,
				       count(d.id) FILTER (WHERE d.status = 'PENDING_REVIEW') AS pending_documents_count,
				       min(d.uploaded_at) FILTER (WHERE d.status = 'PENDING_REVIEW') AS oldest_pending_document_at
				FROM profiles p
				JOIN official_documents d ON d.profile_id = p.id
				WHERE p.type IN ('MOTOTAXI', 'TRANSPORTER')
				""");
		if (profileType != null) {
			sql.append("  AND p.type = ?\n");
			params.add(profileType.name());
		}
		if (status != null) {
			sql.append("  AND p.validation_status = ?\n");
			params.add(status.name());
		}
		sql.append("""
				GROUP BY p.id, p.type, p.validation_status, p.display_name
				""");
		if (status == ValidationStatus.PENDING) {
			sql.append("HAVING count(d.id) FILTER (WHERE d.status = 'PENDING_REVIEW') > 0\n");
		}
		else {
			sql.append("HAVING count(d.id) > 0\n");
		}
		sql.append("""
				ORDER BY oldest_pending_document_at ASC NULLS LAST, p.updated_at ASC
				""");
		return this.jdbcTemplate.query(sql.toString(), this::mapQueueItem, params.toArray());
	}

	public Optional<KycProfileRecord> findProfileById(UUID profileId) {
		return this.jdbcTemplate.query("""
				SELECT id, user_id, type, validation_status, display_name
				FROM profiles
				WHERE id = ?
				""", this::mapProfile, profileId).stream().findFirst();
	}

	public Optional<KycProfileRecord> lockProfileForDecision(UUID profileId) {
		return this.jdbcTemplate.query("""
				SELECT id, user_id, type, validation_status, display_name
				FROM profiles
				WHERE id = ?
				FOR UPDATE
				""", this::mapProfile, profileId).stream().findFirst();
	}

	public List<OfficialDocumentRecord> findDocumentsByProfile(UUID profileId) {
		return this.jdbcTemplate.query("""
				SELECT id, profile_id, vehicle_id, type, status, encrypted_storage_key, file_name, mime_type, size_bytes,
				       rejection_reason, uploaded_at, updated_at
				FROM official_documents
				WHERE profile_id = ?
				ORDER BY uploaded_at DESC
				""", this::mapDocument, profileId);
	}

	public Optional<OfficialDocumentRecord> findDocumentById(UUID documentId) {
		return this.jdbcTemplate.query("""
				SELECT id, profile_id, vehicle_id, type, status, encrypted_storage_key, file_name, mime_type, size_bytes,
				       rejection_reason, uploaded_at, updated_at
				FROM official_documents
				WHERE id = ?
				""", this::mapDocument, documentId).stream().findFirst();
	}

	public Optional<OfficialDocumentRecord> lockDocumentById(UUID documentId) {
		return this.jdbcTemplate.query("""
				SELECT id, profile_id, vehicle_id, type, status, encrypted_storage_key, file_name, mime_type, size_bytes,
				       rejection_reason, uploaded_at, updated_at
				FROM official_documents
				WHERE id = ?
				FOR UPDATE
				""", this::mapDocument, documentId).stream().findFirst();
	}

	public List<OfficialDocumentRecord> findPendingDocumentsByProfile(UUID profileId) {
		return this.jdbcTemplate.query("""
				SELECT id, profile_id, vehicle_id, type, status, encrypted_storage_key, file_name, mime_type, size_bytes,
				       rejection_reason, uploaded_at, updated_at
				FROM official_documents
				WHERE profile_id = ? AND status = 'PENDING_REVIEW'
				ORDER BY uploaded_at ASC
				""", this::mapDocument, profileId);
	}

	public List<ValidationDecisionRecord> findDecisionsByProfile(UUID profileId) {
		return this.jdbcTemplate.query("""
				SELECT id, profile_id, admin_profile_id, decision, reason, decided_at
				FROM validation_decisions
				WHERE profile_id = ?
				ORDER BY decided_at DESC
				""", this::mapDecision, profileId);
	}

	public List<DocumentDecisionRecord> findDocumentDecisionsByProfile(UUID profileId) {
		return this.jdbcTemplate.query("""
				SELECT id, profile_id, document_id, document_type, file_name, mime_type, size_bytes,
				       decision, reason, admin_profile_id, decided_at
				FROM kyc_document_decisions
				WHERE profile_id = ?
				ORDER BY decided_at DESC
				""", this::mapDocumentDecision, profileId);
	}

	public void updateProfileStatus(UUID profileId, ValidationStatus status) {
		this.jdbcTemplate.update("""
				UPDATE profiles
				SET validation_status = ?, updated_at = now()
				WHERE id = ?
				""", status.name(), profileId);
	}

	public void updatePendingDocuments(UUID profileId, DocumentStatus status, String rejectionReason) {
		this.jdbcTemplate.update("""
				UPDATE official_documents
				SET status = ?,
				    rejection_reason = ?,
				    updated_at = now()
				WHERE profile_id = ? AND status = 'PENDING_REVIEW'
				""", status.name(), blankToNull(rejectionReason), profileId);
	}

	public void updateDocumentStatus(UUID documentId, DocumentStatus status, String rejectionReason) {
		this.jdbcTemplate.update("""
				UPDATE official_documents
				SET status = ?,
				    rejection_reason = ?,
				    updated_at = now()
				WHERE id = ?
				""", status.name(), blankToNull(rejectionReason), documentId);
	}

	public void deleteDocument(UUID documentId) {
		this.jdbcTemplate.update("""
				DELETE FROM official_documents
				WHERE id = ?
				""", documentId);
	}

	public long countPendingDocumentsByProfile(UUID profileId) {
		Long count = this.jdbcTemplate.queryForObject("""
				SELECT count(*)
				FROM official_documents
				WHERE profile_id = ? AND status = 'PENDING_REVIEW'
				""", Long.class, profileId);
		return count == null ? 0 : count;
	}

	public long countAcceptedRequiredDocumentTypes(UUID profileId, List<DocumentType> documentTypes) {
		if (documentTypes == null || documentTypes.isEmpty()) {
			return 0;
		}
		List<Object> params = new ArrayList<>();
		params.add(profileId);
		String placeholders = String.join(", ", documentTypes.stream().map(type -> "?").toList());
		documentTypes.stream().map(DocumentType::name).forEach(params::add);
		Long count = this.jdbcTemplate.queryForObject("""
				SELECT count(DISTINCT type)
				FROM official_documents
				WHERE profile_id = ?
				  AND status = 'ACCEPTED'
				  AND type IN (%s)
				""".formatted(placeholders), Long.class, params.toArray());
		return count == null ? 0 : count;
	}

	public OfficialDocumentRecord saveDocument(UUID profileId, UUID vehicleId, DocumentType type, DocumentStatus status,
			String encryptedStorageKey, String fileName, String mimeType, long sizeBytes, String checksum) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO official_documents (
				  profile_id, vehicle_id, type, status, encrypted_storage_key, file_name, mime_type, size_bytes, checksum
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				RETURNING id, profile_id, vehicle_id, type, status, encrypted_storage_key, file_name, mime_type, size_bytes,
				          rejection_reason, uploaded_at, updated_at
				""", this::mapDocument, profileId, vehicleId, type.name(), status.name(), encryptedStorageKey,
				blankToNull(fileName), mimeType, sizeBytes, checksum);
	}

	public ValidationDecisionRecord saveDecision(UUID profileId, UUID adminProfileId, KycDecision decision, String reason) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO validation_decisions (profile_id, admin_profile_id, decision, reason)
				VALUES (?, ?, ?, ?)
				RETURNING id, profile_id, admin_profile_id, decision, reason, decided_at
				""", this::mapDecision, profileId, adminProfileId, decision.name(), blankToNull(reason));
	}

	public DocumentDecisionRecord saveDocumentDecision(OfficialDocumentRecord document, UUID adminProfileId,
			KycDecision decision, String reason) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO kyc_document_decisions (
				  profile_id, document_id, document_type, file_name, mime_type, size_bytes,
				  decision, reason, admin_profile_id
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				RETURNING id, profile_id, document_id, document_type, file_name, mime_type, size_bytes,
				          decision, reason, admin_profile_id, decided_at
				""", this::mapDocumentDecision, document.profileId(), document.id(), document.type().name(),
				blankToNull(document.fileName()), document.mimeType(), document.sizeBytes(), decision.name(),
				blankToNull(reason), adminProfileId);
	}

	public void saveAudit(UUID documentId, UUID actorProfileId, AccessAction action, String actorRole, String correlationId) {
		this.jdbcTemplate.update("""
				INSERT INTO document_access_audits (document_id, actor_profile_id, action, actor_role, correlation_id)
				VALUES (?, ?, ?, ?, ?)
				""", documentId, actorProfileId, action.name(), actorRole, blankToNull(correlationId));
	}

	public void queueKycDecisionNotification(UUID userId, KycDecision decision, String profileDisplayName, String reason) {
		this.jdbcTemplate.update("""
				INSERT INTO notifications (user_id, type, title, body, status, scheduled_at, metadata_json)
				VALUES (?, 'KYC_DECISION', ?, ?, 'QUEUED', now(), ?::jsonb)
				""", userId, notificationTitle(decision), notificationBody(decision, profileDisplayName, reason),
				notificationMetadata(decision, reason));
	}

	private AdminKycQueueItemResponse mapQueueItem(ResultSet rs, int rowNum) throws SQLException {
		return new AdminKycQueueItemResponse(
				rs.getObject("profile_id", UUID.class),
				ProfileType.valueOf(rs.getString("profile_type")),
				ValidationStatus.valueOf(rs.getString("validation_status")),
				rs.getString("display_name"),
				rs.getLong("documents_count"),
				rs.getLong("pending_documents_count"),
				instant(rs, "oldest_pending_document_at"));
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
				rs.getString("file_name"),
				rs.getString("mime_type"),
				rs.getLong("size_bytes"),
				rs.getString("rejection_reason"),
				instant(rs, "uploaded_at"),
				instant(rs, "updated_at"));
	}

	private ValidationDecisionRecord mapDecision(ResultSet rs, int rowNum) throws SQLException {
		return new ValidationDecisionRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("profile_id", UUID.class),
				rs.getObject("admin_profile_id", UUID.class),
				KycDecision.valueOf(rs.getString("decision")),
				rs.getString("reason"),
				instant(rs, "decided_at"));
	}

	private DocumentDecisionRecord mapDocumentDecision(ResultSet rs, int rowNum) throws SQLException {
		return new DocumentDecisionRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("profile_id", UUID.class),
				rs.getObject("document_id", UUID.class),
				DocumentType.valueOf(rs.getString("document_type")),
				rs.getString("file_name"),
				rs.getString("mime_type"),
				rs.getLong("size_bytes"),
				KycDecision.valueOf(rs.getString("decision")),
				rs.getString("reason"),
				rs.getObject("admin_profile_id", UUID.class),
				instant(rs, "decided_at"));
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String notificationTitle(KycDecision decision) {
		return decision == KycDecision.VALIDATE ? "Profil KYC valide" : "Profil KYC rejete";
	}

	private static String notificationBody(KycDecision decision, String displayName, String reason) {
		if (decision == KycDecision.VALIDATE) {
			return "Votre profil " + displayName + " a ete valide.";
		}
		return "Votre profil " + displayName + " a ete rejete. Motif: " + reason;
	}

	private static String notificationMetadata(KycDecision decision, String reason) {
		String status = decision == KycDecision.VALIDATE ? "VALIDATED" : "REJECTED";
		String escapedReason = reason == null ? null : reason.replace("\\", "\\\\").replace("\"", "\\\"");
		return escapedReason == null
				? "{\"type\":\"KYC_DECISION\",\"status\":\"" + status + "\"}"
				: "{\"type\":\"KYC_DECISION\",\"status\":\"" + status + "\",\"reason\":\"" + escapedReason + "\"}";
	}
}
