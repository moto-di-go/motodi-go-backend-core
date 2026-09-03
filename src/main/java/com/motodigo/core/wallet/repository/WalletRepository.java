package com.motodigo.core.wallet.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.wallet.entity.LedgerEntryRecord;
import com.motodigo.core.wallet.entity.LedgerEntryType;
import com.motodigo.core.wallet.entity.WalletRecord;
import com.motodigo.core.wallet.entity.WalletStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class WalletRepository {

	private final JdbcTemplate jdbcTemplate;

	public WalletRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<WalletRecord> findByUserId(UUID userId) {
		return this.jdbcTemplate.query("""
				SELECT * FROM wallets WHERE user_id = ?
				""", this::mapWallet, userId).stream().findFirst();
	}

	public Optional<WalletRecord> findById(UUID walletId) {
		return this.jdbcTemplate.query("""
				SELECT * FROM wallets WHERE id = ?
				""", this::mapWallet, walletId).stream().findFirst();
	}

	/**
	 * Acquires a pessimistic row lock on the wallet for the duration of the surrounding
	 * transaction so concurrent postings serialize on the balance update.
	 */
	public Optional<WalletRecord> lockByIdForUpdate(UUID walletId) {
		return this.jdbcTemplate.query("""
				SELECT * FROM wallets WHERE id = ? FOR UPDATE
				""", this::mapWallet, walletId).stream().findFirst();
	}

	public WalletRecord updateBalance(UUID walletId, BigDecimal newBalance) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE wallets
				SET current_balance = ?, updated_at = now()
				WHERE id = ?
				RETURNING *
				""", this::mapWallet, newBalance, walletId);
	}

	public WalletRecord updateHeldBalance(UUID walletId, BigDecimal heldBalance) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE wallets
				SET held_balance = ?, updated_at = now()
				WHERE id = ?
				RETURNING *
				""", this::mapWallet, heldBalance, walletId);
	}

	public WalletRecord updateBalances(UUID walletId, BigDecimal currentBalance, BigDecimal heldBalance) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE wallets
				SET current_balance = ?, held_balance = ?, updated_at = now()
				WHERE id = ?
				RETURNING *
				""", this::mapWallet, currentBalance, heldBalance, walletId);
	}

	public LedgerEntryRecord insertLedgerEntry(UUID walletId, LedgerEntryType type, BigDecimal amount,
			String reference, String idempotencyKey, UUID paymentTransactionId) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO ledger_entries (wallet_id, payment_transaction_id, type, amount, reference, idempotency_key)
				VALUES (?, ?, ?, ?, ?, ?)
				RETURNING *
				""", this::mapLedgerEntry, walletId, paymentTransactionId, type.name(), amount, reference, idempotencyKey);
	}

	public Optional<LedgerEntryRecord> findLedgerEntryByIdempotencyKey(String idempotencyKey) {
		return this.jdbcTemplate.query("""
				SELECT * FROM ledger_entries WHERE idempotency_key = ?
				""", this::mapLedgerEntry, idempotencyKey).stream().findFirst();
	}

	public List<LedgerEntryRecord> findLedger(UUID walletId, int limit, int offset) {
		return this.jdbcTemplate.query("""
				SELECT * FROM ledger_entries
				WHERE wallet_id = ?
				ORDER BY booked_at DESC, id DESC
				LIMIT ? OFFSET ?
				""", this::mapLedgerEntry, walletId, limit, offset);
	}

	/**
	 * Derives the settled balance directly from the ledger (CREDIT minus DEBIT) so callers and
	 * tests can assert {@code current_balance} stays consistent with the immutable movements.
	 */
	public BigDecimal sumSettledBalance(UUID walletId) {
		BigDecimal sum = this.jdbcTemplate.queryForObject("""
				SELECT COALESCE(SUM(
					CASE type
						WHEN 'CREDIT' THEN amount
						WHEN 'DEBIT' THEN -amount
						ELSE 0
					END), 0)
				FROM ledger_entries
				WHERE wallet_id = ?
				""", BigDecimal.class, walletId);
		return sum == null ? BigDecimal.ZERO : sum;
	}

	private WalletRecord mapWallet(ResultSet rs, int rowNum) throws SQLException {
		BigDecimal held = rs.getBigDecimal("held_balance");
		if (held == null) {
			held = BigDecimal.ZERO;
		}
		return new WalletRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("currency"),
				rs.getBigDecimal("current_balance"),
				held,
				WalletStatus.valueOf(rs.getString("status")),
				instant(rs, "created_at"),
				instant(rs, "updated_at"));
	}

	private LedgerEntryRecord mapLedgerEntry(ResultSet rs, int rowNum) throws SQLException {
		return new LedgerEntryRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("wallet_id", UUID.class),
				rs.getObject("payment_transaction_id", UUID.class),
				LedgerEntryType.valueOf(rs.getString("type")),
				rs.getBigDecimal("amount"),
				rs.getString("currency"),
				rs.getString("reference"),
				instant(rs, "booked_at"),
				rs.getString("idempotency_key"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}
}
