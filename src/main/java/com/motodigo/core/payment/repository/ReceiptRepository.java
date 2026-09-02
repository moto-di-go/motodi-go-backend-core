package com.motodigo.core.payment.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.payment.entity.ReceiptRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ReceiptRepository {

	private final JdbcTemplate jdbcTemplate;

	public ReceiptRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public ReceiptRecord insert(UUID paymentTransactionId, String receiptNumber, BigDecimal amount, String currency,
			String pdfUrl) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO receipts (payment_transaction_id, receipt_number, amount, currency, pdf_url)
				VALUES (?, ?, ?, ?, ?)
				RETURNING *
				""", this::mapReceipt, paymentTransactionId, receiptNumber, amount, currency, pdfUrl);
	}

	public Optional<ReceiptRecord> findByPaymentTransactionId(UUID paymentTransactionId) {
		return this.jdbcTemplate.query("""
				SELECT * FROM receipts WHERE payment_transaction_id = ?
				""", this::mapReceipt, paymentTransactionId).stream().findFirst();
	}

	public List<ReceiptRecord> listForUser(UUID userId, int limit, int offset) {
		return this.jdbcTemplate.query("""
				SELECT r.*
				FROM receipts r
				INNER JOIN payment_transactions pt ON pt.id = r.payment_transaction_id
				LEFT JOIN wallets pw ON pw.id = pt.passenger_wallet_id
				LEFT JOIN wallets rw ON rw.id = pt.receiver_wallet_id
				WHERE pw.user_id = ? OR rw.user_id = ?
				ORDER BY r.issued_at DESC
				LIMIT ? OFFSET ?
				""", this::mapReceipt, userId, userId, limit, offset);
	}

	public Optional<ReceiptRecord> findByIdForUser(UUID receiptId, UUID userId) {
		return this.jdbcTemplate.query("""
				SELECT r.*
				FROM receipts r
				INNER JOIN payment_transactions pt ON pt.id = r.payment_transaction_id
				LEFT JOIN wallets pw ON pw.id = pt.passenger_wallet_id
				LEFT JOIN wallets rw ON rw.id = pt.receiver_wallet_id
				WHERE r.id = ? AND (pw.user_id = ? OR rw.user_id = ?)
				""", this::mapReceipt, receiptId, userId, userId).stream().findFirst();
	}

	private ReceiptRecord mapReceipt(ResultSet rs, int rowNum) throws SQLException {
		OffsetDateTime issuedAt = rs.getObject("issued_at", OffsetDateTime.class);
		return new ReceiptRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("payment_transaction_id", UUID.class),
				rs.getString("receipt_number"),
				rs.getBigDecimal("amount"),
				rs.getString("currency"),
				issuedAt == null ? null : issuedAt.toInstant(),
				rs.getString("pdf_url"));
	}
}
