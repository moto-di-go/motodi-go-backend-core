package com.motodigo.core.payment.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.payment.entity.PaymentProvider;
import com.motodigo.core.payment.entity.PaymentStatus;
import com.motodigo.core.payment.entity.PaymentTransactionRecord;
import com.motodigo.core.payment.entity.PaymentType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PaymentTransactionRepository {

	private final JdbcTemplate jdbcTemplate;

	public PaymentTransactionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public PaymentTransactionRecord insertDeposit(UUID walletId, BigDecimal amount, String currency,
			PaymentProvider provider, String payerPhone, String idempotencyKey, String metadataJson) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO payment_transactions (
					type, status, amount, currency, idempotency_key, provider, payer_phone,
					passenger_wallet_id, metadata)
				VALUES ('MOBILE_MONEY_DEPOSIT', 'INITIATED', ?, ?, ?, ?, ?, ?, ?::jsonb)
				RETURNING *
				""", this::mapTransaction, amount, currency, idempotencyKey, provider.name(), payerPhone, walletId,
				metadataJson == null ? "{}" : metadataJson);
	}

	public Optional<PaymentTransactionRecord> findById(UUID id) {
		return this.jdbcTemplate.query("""
				SELECT * FROM payment_transactions WHERE id = ?
				""", this::mapTransaction, id).stream().findFirst();
	}

	public Optional<PaymentTransactionRecord> findByIdempotencyKey(String idempotencyKey) {
		return this.jdbcTemplate.query("""
				SELECT * FROM payment_transactions WHERE idempotency_key = ?
				""", this::mapTransaction, idempotencyKey).stream().findFirst();
	}

	public Optional<PaymentTransactionRecord> lockByIdForUpdate(UUID id) {
		return this.jdbcTemplate.query("""
				SELECT * FROM payment_transactions WHERE id = ? FOR UPDATE
				""", this::mapTransaction, id).stream().findFirst();
	}

	public Optional<PaymentTransactionRecord> lockByProviderAndExternalReference(PaymentProvider provider,
			String externalReference) {
		return this.jdbcTemplate.query("""
				SELECT * FROM payment_transactions
				WHERE provider = ? AND external_reference = ?
				FOR UPDATE
				""", this::mapTransaction, provider.name(), externalReference).stream().findFirst();
	}

	public PaymentTransactionRecord markPendingCallback(UUID id, String externalReference) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE payment_transactions
				SET status = 'PENDING_CALLBACK', external_reference = ?
				WHERE id = ? AND status = 'INITIATED'
				RETURNING *
				""", this::mapTransaction, externalReference, id);
	}

	public PaymentTransactionRecord markFailed(UUID id, String failureReason) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE payment_transactions
				SET status = 'FAILED', failure_reason = ?
				WHERE id = ? AND status IN ('INITIATED', 'PENDING_CALLBACK')
				RETURNING *
				""", this::mapTransaction, failureReason, id);
	}

	public Optional<PaymentTransactionRecord> markExpired(UUID id, String failureReason) {
		List<PaymentTransactionRecord> rows = this.jdbcTemplate.query("""
				UPDATE payment_transactions
				SET status = 'EXPIRED', failure_reason = ?
				WHERE id = ? AND status = 'PENDING_CALLBACK'
				RETURNING *
				""", this::mapTransaction, failureReason, id);
		return rows.stream().findFirst();
	}

	public PaymentTransactionRecord markConfirmed(UUID id, String callbackSignature) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE payment_transactions
				SET status = 'CONFIRMED', confirmed_at = now(), callback_signature = ?, failure_reason = NULL
				WHERE id = ? AND status = 'PENDING_CALLBACK'
				RETURNING *
				""", this::mapTransaction, callbackSignature, id);
	}

	public PaymentTransactionRecord markCallbackFailure(UUID id, PaymentStatus status, String failureReason,
			String callbackSignature) {
		if (status != PaymentStatus.FAILED && status != PaymentStatus.EXPIRED) {
			throw new IllegalArgumentException("status must be FAILED or EXPIRED");
		}
		return this.jdbcTemplate.queryForObject("""
				UPDATE payment_transactions
				SET status = ?, failure_reason = ?, callback_signature = ?
				WHERE id = ? AND status = 'PENDING_CALLBACK'
				RETURNING *
				""", this::mapTransaction, status.name(), failureReason, callbackSignature, id);
	}

	public Optional<UUID> findWalletOwnerUserId(UUID walletId) {
		return this.jdbcTemplate.query("""
				SELECT user_id FROM wallets WHERE id = ?
				""", (rs, rowNum) -> rs.getObject("user_id", UUID.class), walletId).stream().findFirst();
	}

	public void queuePaymentNotification(UUID userId, UUID paymentTransactionId, String title, String body,
			String metadataJson) {
		this.jdbcTemplate.update("""
				INSERT INTO notifications (
					user_id, type, title, body, status, scheduled_at, payment_transaction_id, metadata_json)
				VALUES (?, 'PAYMENT', ?, ?, 'QUEUED', now(), ?, ?::jsonb)
				""", userId, title, body, paymentTransactionId, metadataJson == null ? "{}" : metadataJson);
	}

	public List<PaymentTransactionRecord> findForAdmin(PaymentProvider provider, PaymentStatus status,
			String externalReference, int limit, int offset) {
		StringBuilder sql = new StringBuilder("""
				SELECT * FROM payment_transactions
				WHERE type = 'MOBILE_MONEY_DEPOSIT'
				""");
		List<Object> args = new ArrayList<>();
		if (provider != null) {
			sql.append(" AND provider = ?");
			args.add(provider.name());
		}
		if (status != null) {
			sql.append(" AND status = ?");
			args.add(status.name());
		}
		if (externalReference != null && !externalReference.isBlank()) {
			sql.append(" AND external_reference = ?");
			args.add(externalReference.trim());
		}
		sql.append(" ORDER BY initiated_at DESC, id DESC LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);
		return this.jdbcTemplate.query(sql.toString(), this::mapTransaction, args.toArray());
	}

	private PaymentTransactionRecord mapTransaction(ResultSet rs, int rowNum) throws SQLException {
		String provider = rs.getString("provider");
		return new PaymentTransactionRecord(
				rs.getObject("id", UUID.class),
				PaymentType.valueOf(rs.getString("type")),
				PaymentStatus.valueOf(rs.getString("status")),
				rs.getBigDecimal("amount"),
				rs.getString("currency"),
				rs.getString("external_reference"),
				rs.getString("idempotency_key"),
				provider == null ? null : PaymentProvider.valueOf(provider),
				rs.getString("payer_phone"),
				rs.getString("callback_signature"),
				rs.getObject("passenger_wallet_id", UUID.class),
				rs.getObject("receiver_wallet_id", UUID.class),
				instant(rs, "initiated_at"),
				instant(rs, "confirmed_at"),
				rs.getString("failure_reason"),
				rs.getString("metadata"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}
}
