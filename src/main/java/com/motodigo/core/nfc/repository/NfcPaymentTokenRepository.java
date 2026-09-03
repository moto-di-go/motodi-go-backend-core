package com.motodigo.core.nfc.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.nfc.entity.NfcPaymentTokenRecord;
import com.motodigo.core.nfc.entity.NfcTokenStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NfcPaymentTokenRepository {

	private final JdbcTemplate jdbcTemplate;

	public NfcPaymentTokenRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public NfcPaymentTokenRecord insert(UUID walletId, UUID jti, String signedToken, BigDecimal amount,
			Instant expiresAt, UUID bookingId, UUID tripId, UUID payerUserId, UUID receiverWalletId,
			String payloadJson) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO nfc_payment_tokens (
					wallet_id, jti, signed_token, amount, expires_at, status,
					booking_id, trip_id, payer_user_id, receiver_wallet_id, payload_json)
				VALUES (?, ?, ?, ?, ?, 'ISSUED', ?, ?, ?, ?, ?::jsonb)
				RETURNING *
				""", this::map, walletId, jti, signedToken, amount, OffsetDateTime.ofInstant(expiresAt,
				java.time.ZoneOffset.UTC), bookingId, tripId, payerUserId, receiverWalletId,
				payloadJson == null ? "{}" : payloadJson);
	}

	public Optional<NfcPaymentTokenRecord> findById(UUID id) {
		return this.jdbcTemplate.query("""
				SELECT * FROM nfc_payment_tokens WHERE id = ?
				""", this::map, id).stream().findFirst();
	}

	public Optional<NfcPaymentTokenRecord> findByJti(UUID jti) {
		return this.jdbcTemplate.query("""
				SELECT * FROM nfc_payment_tokens WHERE jti = ?
				""", this::map, jti).stream().findFirst();
	}

	public Optional<NfcPaymentTokenRecord> findBySignedToken(String signedToken) {
		return this.jdbcTemplate.query("""
				SELECT * FROM nfc_payment_tokens WHERE signed_token = ?
				""", this::map, signedToken).stream().findFirst();
	}

	public Optional<NfcPaymentTokenRecord> lockByJtiForUpdate(UUID jti) {
		return this.jdbcTemplate.query("""
				SELECT * FROM nfc_payment_tokens WHERE jti = ? FOR UPDATE
				""", this::map, jti).stream().findFirst();
	}

	public NfcPaymentTokenRecord updateStatus(UUID id, NfcTokenStatus status, Instant consumedAt,
			UUID paymentTransactionId) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE nfc_payment_tokens
				SET status = ?, consumed_at = COALESCE(?, consumed_at),
					payment_transaction_id = COALESCE(?, payment_transaction_id)
				WHERE id = ?
				RETURNING *
				""", this::map, status.name(),
				consumedAt == null ? null : OffsetDateTime.ofInstant(consumedAt, java.time.ZoneOffset.UTC),
				paymentTransactionId, id);
	}

	private NfcPaymentTokenRecord map(ResultSet rs, int rowNum) throws SQLException {
		return new NfcPaymentTokenRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("wallet_id", UUID.class),
				rs.getObject("payment_transaction_id", UUID.class),
				rs.getString("signed_token"),
				rs.getBigDecimal("amount"),
				instant(rs, "issued_at"),
				instant(rs, "expires_at"),
				instant(rs, "consumed_at"),
				NfcTokenStatus.valueOf(rs.getString("status")),
				rs.getObject("jti", UUID.class),
				rs.getObject("booking_id", UUID.class),
				rs.getObject("trip_id", UUID.class),
				rs.getObject("payer_user_id", UUID.class),
				rs.getObject("receiver_wallet_id", UUID.class),
				rs.getString("payload_json"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}
}
