package com.motodigo.core.offline.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.offline.entity.OfflineOperationRecord;
import com.motodigo.core.offline.entity.SyncBatchRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class OfflineSyncRepository {

	private final JdbcTemplate jdbcTemplate;

	public OfflineSyncRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public SyncBatchRecord insertBatch(UUID userId) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO sync_batches (user_id, status)
				VALUES (?, 'STARTED')
				RETURNING *
				""", this::mapBatch, userId);
	}

	public SyncBatchRecord completeBatch(UUID batchId, String status, Instant completedAt) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE sync_batches
				SET status = ?, completed_at = ?
				WHERE id = ?
				RETURNING *
				""", this::mapBatch, status, Timestamp.from(completedAt), batchId);
	}

	public Optional<OfflineOperationRecord> findByUserAndIdempotency(UUID userId, String idempotencyKey) {
		return this.jdbcTemplate.query("""
				SELECT o.*,
				       c.code AS conflict_code,
				       c.message AS conflict_message
				FROM offline_operations o
				LEFT JOIN sync_conflicts c ON c.offline_operation_id = o.id
				WHERE o.user_id = ? AND o.idempotency_key = ?
				""", this::mapOperation, userId, idempotencyKey).stream().findFirst();
	}

	public OfflineOperationRecord insertOperation(UUID userId, UUID syncBatchId, String type, String payloadJson,
			String idempotencyKey, Instant queuedAt) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO offline_operations (
				  user_id, sync_batch_id, type, payload, idempotency_key, status, queued_at)
				VALUES (?, ?, ?, ?::jsonb, ?, 'SYNCING', ?)
				RETURNING id, user_id, sync_batch_id, type, payload::text AS payload_json, idempotency_key, status,
				          retry_count, queued_at, synced_at, booking_id, nfc_token_id,
				          NULL::text AS conflict_code, NULL::text AS conflict_message
				""", this::mapOperation, userId, syncBatchId, type, payloadJson, idempotencyKey,
				Timestamp.from(queuedAt));
	}

	public OfflineOperationRecord markSynced(UUID operationId, UUID nfcTokenId, UUID bookingId, Instant syncedAt) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE offline_operations
				SET status = 'SYNCED', synced_at = ?, nfc_token_id = COALESCE(?, nfc_token_id),
				    booking_id = COALESCE(?, booking_id), sync_batch_id = COALESCE(sync_batch_id, sync_batch_id)
				WHERE id = ?
				RETURNING id, user_id, sync_batch_id, type, payload::text AS payload_json, idempotency_key, status,
				          retry_count, queued_at, synced_at, booking_id, nfc_token_id,
				          NULL::text AS conflict_code, NULL::text AS conflict_message
				""", this::mapOperation, Timestamp.from(syncedAt), nfcTokenId, bookingId, operationId);
	}

	public OfflineOperationRecord markConflict(UUID operationId, String code, String message) {
		this.jdbcTemplate.update("""
				INSERT INTO sync_conflicts (offline_operation_id, code, message)
				VALUES (?, ?, ?)
				ON CONFLICT (offline_operation_id) DO UPDATE
				SET code = EXCLUDED.code, message = EXCLUDED.message, detected_at = now()
				""", operationId, code, message);
		return this.jdbcTemplate.queryForObject("""
				UPDATE offline_operations
				SET status = 'CONFLICT'
				WHERE id = ?
				RETURNING id, user_id, sync_batch_id, type, payload::text AS payload_json, idempotency_key, status,
				          retry_count, queued_at, synced_at, booking_id, nfc_token_id,
				          ? AS conflict_code, ? AS conflict_message
				""", this::mapOperation, operationId, code, message);
	}

	public OfflineOperationRecord markFailed(UUID operationId, String message) {
		this.jdbcTemplate.update("""
				INSERT INTO sync_conflicts (offline_operation_id, code, message)
				VALUES (?, 'FAILED', ?)
				ON CONFLICT (offline_operation_id) DO UPDATE
				SET code = EXCLUDED.code, message = EXCLUDED.message, detected_at = now()
				""", operationId, message == null ? "FAILED" : message);
		return this.jdbcTemplate.queryForObject("""
				UPDATE offline_operations
				SET status = 'FAILED', retry_count = retry_count + 1
				WHERE id = ?
				RETURNING id, user_id, sync_batch_id, type, payload::text AS payload_json, idempotency_key, status,
				          retry_count, queued_at, synced_at, booking_id, nfc_token_id,
				          'FAILED' AS conflict_code, ? AS conflict_message
				""", this::mapOperation, operationId, message);
	}

	public OfflineOperationRecord markIgnored(UUID operationId) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE offline_operations
				SET status = 'SYNCED', synced_at = COALESCE(synced_at, now())
				WHERE id = ?
				RETURNING id, user_id, sync_batch_id, type, payload::text AS payload_json, idempotency_key, status,
				          retry_count, queued_at, synced_at, booking_id, nfc_token_id,
				          NULL::text AS conflict_code, NULL::text AS conflict_message
				""", this::mapOperation, operationId);
	}

	public List<OfflineOperationRecord> listForUser(UUID userId, String status, int limit, int offset) {
		if (status == null || status.isBlank()) {
			return this.jdbcTemplate.query("""
					SELECT o.*,
					       c.code AS conflict_code,
					       c.message AS conflict_message,
					       o.payload::text AS payload_json
					FROM offline_operations o
					LEFT JOIN sync_conflicts c ON c.offline_operation_id = o.id
					WHERE o.user_id = ?
					ORDER BY o.queued_at DESC
					LIMIT ? OFFSET ?
					""", this::mapOperation, userId, limit, offset);
		}
		return this.jdbcTemplate.query("""
				SELECT o.*,
				       c.code AS conflict_code,
				       c.message AS conflict_message,
				       o.payload::text AS payload_json
				FROM offline_operations o
				LEFT JOIN sync_conflicts c ON c.offline_operation_id = o.id
				WHERE o.user_id = ? AND o.status = ?
				ORDER BY o.queued_at DESC
				LIMIT ? OFFSET ?
				""", this::mapOperation, userId, status.trim().toUpperCase(Locale.ROOT), limit, offset);
	}

	private SyncBatchRecord mapBatch(ResultSet rs, int rowNum) throws SQLException {
		return new SyncBatchRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				toInstant(rs.getObject("started_at")),
				toInstantNullable(rs.getObject("completed_at")),
				rs.getString("status"));
	}

	private OfflineOperationRecord mapOperation(ResultSet rs, int rowNum) throws SQLException {
		String payloadJson = null;
		try {
			payloadJson = rs.getString("payload_json");
		}
		catch (SQLException ignored) {
			Object payload = rs.getObject("payload");
			payloadJson = payload == null ? "{}" : payload.toString();
		}
		return new OfflineOperationRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getObject("sync_batch_id", UUID.class),
				rs.getString("type"),
				payloadJson,
				rs.getString("idempotency_key"),
				rs.getString("status"),
				rs.getInt("retry_count"),
				toInstant(rs.getObject("queued_at")),
				toInstantNullable(rs.getObject("synced_at")),
				rs.getObject("booking_id", UUID.class),
				rs.getObject("nfc_token_id", UUID.class),
				rs.getString("conflict_code"),
				rs.getString("conflict_message"));
	}

	private static Instant toInstant(Object value) {
		Instant instant = toInstantNullable(value);
		if (instant == null) {
			throw new IllegalArgumentException("Timestamp required");
		}
		return instant;
	}

	private static Instant toInstantNullable(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof OffsetDateTime odt) {
			return odt.toInstant();
		}
		if (value instanceof Timestamp ts) {
			return ts.toInstant();
		}
		if (value instanceof Instant instant) {
			return instant;
		}
		throw new IllegalArgumentException("Unsupported timestamp: " + value);
	}
}
