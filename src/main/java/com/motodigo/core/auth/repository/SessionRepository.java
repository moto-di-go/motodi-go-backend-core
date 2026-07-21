package com.motodigo.core.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.auth.entity.SessionRecord;
import com.motodigo.core.auth.entity.SessionStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class SessionRepository {

	private final JdbcTemplate jdbcTemplate;

	public SessionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public SessionRecord save(UUID sessionId, UUID userId, String accessTokenHash, String refreshTokenHash,
			String deviceId, String ipAddress, Instant expiresAt) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO sessions (id, user_id, access_token_hash, refresh_token_hash, device_id, ip_address, expires_at)
				VALUES (?, ?, ?, ?, ?, CAST(? AS inet), ?)
				RETURNING *
				""", this::mapSession, sessionId, userId, accessTokenHash, refreshTokenHash, deviceId, ipAddress,
				JdbcInstantMapper.timestamp(expiresAt));
	}

	public Optional<SessionRecord> findActiveByRefreshTokenHash(String refreshTokenHash) {
		return this.jdbcTemplate.query("""
				SELECT * FROM sessions
				WHERE refresh_token_hash = ?
				  AND status = 'ACTIVE'
				  AND revoked_at IS NULL
				  AND expires_at > now()
				""", this::mapSession, refreshTokenHash).stream().findFirst();
	}

	public boolean isActiveAccessToken(UUID sessionId, String accessTokenHash) {
		Boolean active = this.jdbcTemplate.queryForObject("""
				SELECT EXISTS (
				  SELECT 1
				  FROM sessions
				  WHERE id = ?
				    AND access_token_hash = ?
				    AND status = 'ACTIVE'
				    AND revoked_at IS NULL
				    AND expires_at > now()
				)
				""", Boolean.class, sessionId, accessTokenHash);
		return Boolean.TRUE.equals(active);
	}

	public void rotate(UUID sessionId, String accessTokenHash, String refreshTokenHash, String deviceId,
			String ipAddress, Instant expiresAt) {
		this.jdbcTemplate.update("""
				UPDATE sessions
				SET access_token_hash = ?,
				    refresh_token_hash = ?,
				    device_id = COALESCE(?, device_id),
				    ip_address = CAST(? AS inet),
				    issued_at = now(),
				    expires_at = ?,
				    status = 'ACTIVE',
				    revoked_at = NULL
				WHERE id = ?
				""", accessTokenHash, refreshTokenHash, deviceId, ipAddress, JdbcInstantMapper.timestamp(expiresAt), sessionId);
	}

	public void revoke(UUID sessionId) {
		this.jdbcTemplate.update("""
				UPDATE sessions
				SET status = 'REVOKED', revoked_at = now()
				WHERE id = ? AND status = 'ACTIVE'
				""", sessionId);
	}

	public void revokeAllForUser(UUID userId) {
		this.jdbcTemplate.update("""
				UPDATE sessions
				SET status = 'REVOKED', revoked_at = now()
				WHERE user_id = ? AND status = 'ACTIVE'
				""", userId);
	}

	private SessionRecord mapSession(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new SessionRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("access_token_hash"),
				rs.getString("refresh_token_hash"),
				rs.getString("device_id"),
				rs.getString("ip_address"),
				JdbcInstantMapper.instant(rs, "issued_at"),
				JdbcInstantMapper.instant(rs, "expires_at"),
				JdbcInstantMapper.instant(rs, "revoked_at"),
				SessionStatus.valueOf(rs.getString("status")));
	}
}
