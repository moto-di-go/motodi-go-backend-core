package com.motodigo.core.notification.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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
public class NotificationInboxRepository {

	private static final String SELECT_BASE = """
			SELECT id, user_id, type, title, body, status, scheduled_at, sent_at, read_at,
			       booking_id, payment_transaction_id, trip_id, created_at, metadata_json::text AS metadata_json
			FROM notifications
			""";

	private final JdbcTemplate jdbcTemplate;

	public NotificationInboxRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<NotificationRecord> findPage(UUID userId, boolean unreadOnly, int limit, int offset) {
		if (unreadOnly) {
			return this.jdbcTemplate.query(SELECT_BASE + """
					WHERE user_id = ? AND read_at IS NULL
					ORDER BY created_at DESC, id DESC
					LIMIT ? OFFSET ?
					""", this::mapRow, userId, limit, offset);
		}
		return this.jdbcTemplate.query(SELECT_BASE + """
				WHERE user_id = ?
				ORDER BY created_at DESC, id DESC
				LIMIT ? OFFSET ?
				""", this::mapRow, userId, limit, offset);
	}

	public long countUnread(UUID userId) {
		Long count = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
				Long.class, userId);
		return count == null ? 0 : count;
	}

	public Optional<NotificationRecord> findOwned(UUID userId, UUID notificationId) {
		return this.jdbcTemplate.query(SELECT_BASE + "WHERE id = ? AND user_id = ?", this::mapRow, notificationId, userId)
				.stream()
				.findFirst();
	}

	public Optional<NotificationRecord> markRead(UUID userId, UUID notificationId, Instant readAt) {
		return this.jdbcTemplate.query("""
				UPDATE notifications
				SET status = 'READ', read_at = COALESCE(read_at, ?)
				WHERE id = ? AND user_id = ?
				RETURNING id, user_id, type, title, body, status, scheduled_at, sent_at, read_at,
				          booking_id, payment_transaction_id, trip_id, created_at, metadata_json::text AS metadata_json
				""", this::mapRow, readAt, notificationId, userId).stream().findFirst();
	}

	public int markAllRead(UUID userId, Instant readAt) {
		return this.jdbcTemplate.update("""
				UPDATE notifications
				SET status = 'READ', read_at = ?
				WHERE user_id = ? AND read_at IS NULL
				""", readAt, userId);
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
}
