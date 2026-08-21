package com.motodigo.core.notification.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.notification.entity.NotificationRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationDispatchRepository {

	private static final String SELECT_BASE = """
			SELECT id, user_id, type, title, body, status, scheduled_at, sent_at, read_at,
			       booking_id, payment_transaction_id, trip_id, created_at, metadata_json::text AS metadata_json
			FROM notifications
			""";

	private final JdbcTemplate jdbcTemplate;

	public NotificationDispatchRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public NotificationRecord insertQueued(UUID userId, String type, String title, String body, UUID bookingId,
			UUID paymentTransactionId, UUID tripId, String metadataJson, Instant scheduledAt) {
		return this.jdbcTemplate.query("""
				INSERT INTO notifications (
					user_id, type, title, body, status, scheduled_at, booking_id, payment_transaction_id, trip_id,
					metadata_json)
				VALUES (?, ?, ?, ?, 'QUEUED', ?, ?, ?, ?, ?::jsonb)
				RETURNING id, user_id, type, title, body, status, scheduled_at, sent_at, read_at,
				          booking_id, payment_transaction_id, trip_id, created_at,
				          metadata_json::text AS metadata_json
				""", this::mapRow, userId, type, title, body, timestamp(scheduledAt), bookingId, paymentTransactionId,
				tripId, metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson)
				.stream()
				.findFirst()
				.orElseThrow();
	}

	public List<NotificationRecord> findQueuedDue(Instant now, int limit) {
		return this.jdbcTemplate.query(SELECT_BASE + """
				WHERE status = 'QUEUED'
				  AND (scheduled_at IS NULL OR scheduled_at <= ?)
				ORDER BY scheduled_at NULLS FIRST, created_at ASC
				LIMIT ?
				""", this::mapRow, timestamp(now), limit);
	}

	public Optional<NotificationRecord> markSent(UUID id, Instant sentAt) {
		return this.jdbcTemplate.query("""
				UPDATE notifications
				SET status = 'SENT', sent_at = COALESCE(sent_at, ?)
				WHERE id = ? AND status = 'QUEUED'
				RETURNING id, user_id, type, title, body, status, scheduled_at, sent_at, read_at,
				          booking_id, payment_transaction_id, trip_id, created_at,
				          metadata_json::text AS metadata_json
				""", this::mapRow, timestamp(sentAt), id).stream().findFirst();
	}

	public Optional<NotificationRecord> markFailed(UUID id) {
		return this.jdbcTemplate.query("""
				UPDATE notifications
				SET status = 'FAILED'
				WHERE id = ? AND status = 'QUEUED'
				RETURNING id, user_id, type, title, body, status, scheduled_at, sent_at, read_at,
				          booking_id, payment_transaction_id, trip_id, created_at,
				          metadata_json::text AS metadata_json
				""", this::mapRow, id).stream().findFirst();
	}

	private NotificationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new NotificationRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("type"),
				rs.getString("title"),
				rs.getString("body"),
				rs.getString("status"),
				instant(rs, "scheduled_at"),
				instant(rs, "sent_at"),
				instant(rs, "read_at"),
				rs.getObject("booking_id", UUID.class),
				rs.getObject("payment_transaction_id", UUID.class),
				rs.getObject("trip_id", UUID.class),
				instant(rs, "created_at"),
				rs.getString("metadata_json"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}
}
