package com.motodigo.core.nfc.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NfcOfflineQuotaRepository {

	public record NfcOfflineQuotaRecord(
			UUID id,
			UUID userId,
			UUID walletId,
			BigDecimal remainingAmount,
			String currency,
			Instant expiresAt,
			Instant updatedAt) {
	}

	private final JdbcTemplate jdbcTemplate;

	public NfcOfflineQuotaRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<NfcOfflineQuotaRecord> findByUserId(UUID userId) {
		return this.jdbcTemplate.query("""
				SELECT * FROM nfc_offline_quotas WHERE user_id = ?
				""", this::map, userId).stream().findFirst();
	}

	public NfcOfflineQuotaRecord upsert(UUID userId, UUID walletId, BigDecimal remainingAmount, Instant expiresAt) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO nfc_offline_quotas (user_id, wallet_id, remaining_amount, currency, expires_at, updated_at)
				VALUES (?, ?, ?, 'XAF', ?, now())
				ON CONFLICT (user_id) DO UPDATE SET
					wallet_id = EXCLUDED.wallet_id,
					remaining_amount = EXCLUDED.remaining_amount,
					expires_at = EXCLUDED.expires_at,
					updated_at = now()
				RETURNING *
				""", this::map, userId, walletId, remainingAmount,
				OffsetDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC));
	}

	private NfcOfflineQuotaRecord map(ResultSet rs, int rowNum) throws SQLException {
		OffsetDateTime expires = rs.getObject("expires_at", OffsetDateTime.class);
		OffsetDateTime updated = rs.getObject("updated_at", OffsetDateTime.class);
		return new NfcOfflineQuotaRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getObject("wallet_id", UUID.class),
				rs.getBigDecimal("remaining_amount"),
				rs.getString("currency"),
				expires == null ? null : expires.toInstant(),
				updated == null ? null : updated.toInstant());
	}
}
