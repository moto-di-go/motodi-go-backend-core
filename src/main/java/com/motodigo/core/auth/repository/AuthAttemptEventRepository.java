package com.motodigo.core.auth.repository;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.auth.entity.AuthEventType;
import com.motodigo.core.auth.service.RequestMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AuthAttemptEventRepository {

	private final JdbcTemplate jdbcTemplate;

	public AuthAttemptEventRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void save(UUID userId, String phoneNumber, AuthEventType eventType, boolean success, String reasonCode,
			RequestMetadata metadata) {
		this.jdbcTemplate.update("""
				INSERT INTO auth_attempt_events
				  (user_id, phone_number, event_type, success, reason_code, ip_address, user_agent, correlation_id)
				VALUES (?, ?, ?, ?, ?, CAST(? AS inet), ?, ?)
				""",
				userId,
				phoneNumber,
				eventType.name(),
				success,
				reasonCode,
				metadata == null ? null : metadata.ipAddress(),
				metadata == null ? null : metadata.userAgent(),
				metadata == null ? null : metadata.correlationId());
	}

	public int countOtpSendsByIpSince(String ipAddress, Instant since) {
		if (ipAddress == null || ipAddress.isBlank()) {
			return 0;
		}
		Integer count = this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM auth_attempt_events
				WHERE event_type = 'OTP_SEND'
				  AND ip_address = CAST(? AS inet)
				  AND created_at >= ?
				""", Integer.class, ipAddress, JdbcInstantMapper.timestamp(since));
		return count == null ? 0 : count;
	}
}
