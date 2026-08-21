package com.motodigo.core.notification.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.notification.entity.DevicePlatform;
import com.motodigo.core.notification.entity.NotificationDeviceRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationDeviceRepository {

	private final JdbcTemplate jdbcTemplate;

	public NotificationDeviceRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public NotificationDeviceRecord upsert(UUID userId, String token, DevicePlatform platform, Instant updatedAt) {
		return this.jdbcTemplate.query("""
				INSERT INTO notification_devices (user_id, token, platform, updated_at)
				VALUES (?, ?, ?, ?)
				ON CONFLICT (token) DO UPDATE
				SET user_id = EXCLUDED.user_id,
				    platform = EXCLUDED.platform,
				    updated_at = EXCLUDED.updated_at
				RETURNING id, user_id, token, platform, updated_at, created_at
				""", this::mapRow, userId, token, platform.name(),
				updatedAt == null ? null : Timestamp.from(updatedAt)).stream().findFirst().orElseThrow();
	}

	public List<String> findTokensByUserId(UUID userId) {
		return this.jdbcTemplate.queryForList(
				"SELECT token FROM notification_devices WHERE user_id = ? ORDER BY updated_at DESC",
				String.class, userId);
	}

	public Optional<NotificationDeviceRecord> findByToken(String token) {
		return this.jdbcTemplate.query("""
				SELECT id, user_id, token, platform, updated_at, created_at
				FROM notification_devices
				WHERE token = ?
				""", this::mapRow, token).stream().findFirst();
	}

	public int deleteByUserIdAndToken(UUID userId, String token) {
		return this.jdbcTemplate.update(
				"DELETE FROM notification_devices WHERE user_id = ? AND token = ?",
				userId, token);
	}

	private NotificationDeviceRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new NotificationDeviceRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("token"),
				DevicePlatform.valueOf(rs.getString("platform")),
				instant(rs, "updated_at"),
				instant(rs, "created_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}
}
