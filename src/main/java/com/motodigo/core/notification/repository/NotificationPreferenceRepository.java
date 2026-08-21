package com.motodigo.core.notification.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.notification.entity.NotificationPreferenceRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationPreferenceRepository {

	private final JdbcTemplate jdbcTemplate;

	public NotificationPreferenceRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<NotificationPreferenceRecord> findByUserId(UUID userId) {
		return this.jdbcTemplate.query("""
				SELECT id, user_id, push_enabled, booking_updates, payment_alerts, marketing_enabled, updated_at
				FROM notification_preferences
				WHERE user_id = ?
				""", this::mapRow, userId).stream().findFirst();
	}

	public NotificationPreferenceRecord upsert(UUID userId, boolean pushEnabled, boolean bookingUpdates,
			boolean paymentAlerts, boolean marketingEnabled, Instant updatedAt) {
		return this.jdbcTemplate.query("""
				INSERT INTO notification_preferences (
					user_id, push_enabled, booking_updates, payment_alerts, marketing_enabled, updated_at)
				VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT (user_id) DO UPDATE
				SET push_enabled = EXCLUDED.push_enabled,
				    booking_updates = EXCLUDED.booking_updates,
				    payment_alerts = EXCLUDED.payment_alerts,
				    marketing_enabled = EXCLUDED.marketing_enabled,
				    updated_at = EXCLUDED.updated_at
				RETURNING id, user_id, push_enabled, booking_updates, payment_alerts, marketing_enabled, updated_at
				""", this::mapRow, userId, pushEnabled, bookingUpdates, paymentAlerts, marketingEnabled,
				updatedAt == null ? null : Timestamp.from(updatedAt))
				.stream()
				.findFirst()
				.orElseThrow();
	}

	public NotificationPreferenceRecord ensureDefaults(UUID userId, Instant updatedAt) {
		return findByUserId(userId).orElseGet(() -> upsert(userId, true, true, true, false, updatedAt));
	}

	private NotificationPreferenceRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new NotificationPreferenceRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getBoolean("push_enabled"),
				rs.getBoolean("booking_updates"),
				rs.getBoolean("payment_alerts"),
				rs.getBoolean("marketing_enabled"),
				instant(rs, "updated_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}
}
