package com.motodigo.core.payment.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
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
