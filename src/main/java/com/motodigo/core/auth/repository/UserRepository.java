package com.motodigo.core.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.auth.entity.UserRecord;
import com.motodigo.core.auth.entity.UserStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class UserRepository {

	private final JdbcTemplate jdbcTemplate;

	public UserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public boolean existsByPhoneNumber(String phoneNumber) {
		Integer count = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM users WHERE phone_number = ?",
				Integer.class,
				phoneNumber);
		return count != null && count > 0;
	}

	public UserRecord savePending(String phoneNumber, String passwordHash, String fullName, String email, String locale) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO users (phone_number, password_hash, full_name, email, locale, cgu_accepted, status)
				VALUES (?, ?, ?, ?, ?, true, 'PENDING_PHONE_VERIFICATION')
				RETURNING *
				""", this::mapUser, phoneNumber, passwordHash, fullName, email, locale == null || locale.isBlank() ? "fr-CM" : locale);
	}

	public Optional<UserRecord> findByPhoneNumber(String phoneNumber) {
		return this.jdbcTemplate.query("""
				SELECT * FROM users WHERE phone_number = ?
				""", this::mapUser, phoneNumber).stream().findFirst();
	}

	public Optional<UserRecord> findById(UUID userId) {
		return this.jdbcTemplate.query("""
				SELECT * FROM users WHERE id = ?
				""", this::mapUser, userId).stream().findFirst();
	}

	public UserRecord activatePhone(UUID userId) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE users
				SET status = 'ACTIVE', phone_verified_at = now(), updated_at = now()
				WHERE id = ?
				RETURNING *
				""", this::mapUser, userId);
	}

	public void updatePassword(UUID userId, String passwordHash) {
		this.jdbcTemplate.update("""
				UPDATE users
				SET password_hash = ?, updated_at = now()
				WHERE id = ?
				""", passwordHash, userId);
	}

	private UserRecord mapUser(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new UserRecord(
				rs.getObject("id", UUID.class),
				rs.getString("phone_number"),
				rs.getString("password_hash"),
				rs.getString("full_name"),
				rs.getString("email"),
				rs.getString("locale"),
				rs.getBoolean("cgu_accepted"),
				UserStatus.valueOf(rs.getString("status")),
				JdbcInstantMapper.instant(rs, "phone_verified_at"),
				JdbcInstantMapper.instant(rs, "created_at"),
				JdbcInstantMapper.instant(rs, "updated_at"));
	}
}
