package com.motodigo.core.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.auth.entity.OtpPurpose;
import com.motodigo.core.auth.entity.OtpRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class OtpRepository {

	private final JdbcTemplate jdbcTemplate;

	public OtpRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public OtpRecord save(UUID userId, String phoneNumber, OtpPurpose purpose, String codeHash, Instant expiresAt) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO otp_codes (user_id, destination_phone, purpose, code_hash, expires_at)
				VALUES (?, ?, ?, ?, ?)
				RETURNING *
				""", this::mapOtp, userId, phoneNumber, purpose.name(), codeHash, JdbcInstantMapper.timestamp(expiresAt));
	}

	public Optional<OtpRecord> findLatest(String phoneNumber, OtpPurpose purpose) {
		return this.jdbcTemplate.query("""
				SELECT * FROM otp_codes
				WHERE destination_phone = ? AND purpose = ?
				ORDER BY created_at DESC
				LIMIT 1
				""", this::mapOtp, phoneNumber, purpose.name()).stream().findFirst();
	}

	public Optional<OtpRecord> findLatestActive(String phoneNumber, OtpPurpose purpose) {
		return this.jdbcTemplate.query("""
				SELECT * FROM otp_codes
				WHERE destination_phone = ?
				  AND purpose = ?
				  AND consumed_at IS NULL
				  AND expires_at > now()
				ORDER BY created_at DESC
				LIMIT 1
				""", this::mapOtp, phoneNumber, purpose.name()).stream().findFirst();
	}

	public int countCreatedSince(String phoneNumber, OtpPurpose purpose, Instant since) {
		Integer count = this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM otp_codes
				WHERE destination_phone = ? AND purpose = ? AND created_at >= ?
				""", Integer.class, phoneNumber, purpose.name(), JdbcInstantMapper.timestamp(since));
		return count == null ? 0 : count;
	}

	public int incrementAttempts(UUID otpId) {
		return this.jdbcTemplate.update("""
				UPDATE otp_codes
				SET attempts = attempts + 1
				WHERE id = ?
				  AND consumed_at IS NULL
				  AND expires_at > now()
				""", otpId);
	}

	public boolean consumeIfActive(UUID otpId, int maxAttempts) {
		int updated = this.jdbcTemplate.update("""
				UPDATE otp_codes
				SET consumed_at = now()
				WHERE id = ?
				  AND consumed_at IS NULL
				  AND attempts < ?
				  AND expires_at > now()
				""", otpId, maxAttempts);
		return updated == 1;
	}

	private OtpRecord mapOtp(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new OtpRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("destination_phone"),
				OtpPurpose.valueOf(rs.getString("purpose")),
				rs.getString("code_hash"),
				rs.getInt("attempts"),
				JdbcInstantMapper.instant(rs, "expires_at"),
				JdbcInstantMapper.instant(rs, "consumed_at"),
				JdbcInstantMapper.instant(rs, "created_at"));
	}
}
