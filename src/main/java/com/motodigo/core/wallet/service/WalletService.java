package com.motodigo.core.wallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.wallet.dto.LedgerEntryResponse;
import com.motodigo.core.wallet.dto.LedgerPageResponse;
import com.motodigo.core.wallet.dto.WalletResponse;
import com.motodigo.core.wallet.entity.LedgerEntryRecord;
import com.motodigo.core.wallet.entity.LedgerEntryType;
import com.motodigo.core.wallet.entity.WalletRecord;
import com.motodigo.core.wallet.entity.WalletStatus;
import com.motodigo.core.wallet.exception.WalletErrorCode;
import com.motodigo.core.wallet.exception.WalletException;
import com.motodigo.core.wallet.repository.WalletRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wallet balance and accounting ledger (CORE-WALLET-001).
 *
 * <p>Read operations expose the user's balance and movement history. Write operations go through
 * {@link #postLedgerEntry} — the atomic, idempotent posting primitive that guarantees accounting
 * integrity under concurrency. There is intentionally no public write endpoint: deposits and ride
 * payments are orchestrated by the payment layer (CORE-PAY-001 / CORE-PAY-002), which calls this
 * primitive.
 */
@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class WalletService {

	private final WalletRepository walletRepository;

	public WalletService(WalletRepository walletRepository) {
		this.walletRepository = walletRepository;
	}

	@Transactional(readOnly = true)
	public WalletResponse getWallet(UUID userId) {
		return WalletResponse.from(requireWalletByUser(userId));
	}

	@Transactional(readOnly = true)
	public LedgerPageResponse getLedger(UUID userId, int page, int size) {
		WalletRecord wallet = requireWalletByUser(userId);
		// Fetch one extra row to determine whether a further page exists.
		List<LedgerEntryRecord> rows = this.walletRepository.findLedger(wallet.id(), size + 1, page * size);
		boolean hasMore = rows.size() > size;
		List<LedgerEntryResponse> entries = rows.stream()
				.limit(size)
				.map(LedgerEntryResponse::from)
				.toList();
		return new LedgerPageResponse(entries, page, size, hasMore);
	}

	/**
	 * Atomically posts a ledger entry and, for settled types, updates the wallet balance.
	 *
	 * <p>Guarantees:
	 * <ul>
	 * <li>Idempotency: if {@code idempotencyKey} is provided and already posted, the existing entry
	 * is returned without a second posting (covers callback/offline replays).</li>
	 * <li>Atomicity: the wallet row is locked {@code FOR UPDATE}; the ledger insert and balance
	 * update happen in the same transaction, so any failure rolls the whole unit back.</li>
	 * <li>Integrity: a DEBIT that would drive the balance negative is refused with
	 * {@link WalletErrorCode#INSUFFICIENT_BALANCE} before any write.</li>
	 * </ul>
	 */
	@Transactional
	public LedgerEntryRecord postLedgerEntry(UUID walletId, LedgerEntryType type, BigDecimal amount,
			String reference, String idempotencyKey, UUID paymentTransactionId) {
		if (amount == null || amount.signum() <= 0) {
			throw new WalletException(WalletErrorCode.WALLET_VALIDATION_FAILED);
		}
		if (idempotencyKey != null) {
			var existing = this.walletRepository.findLedgerEntryByIdempotencyKey(idempotencyKey);
			if (existing.isPresent()) {
				return existing.get();
			}
		}

		WalletRecord wallet = this.walletRepository.lockByIdForUpdate(walletId)
				.orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));
		if (wallet.status() != WalletStatus.ACTIVE) {
			throw new WalletException(WalletErrorCode.WALLET_NOT_ACTIVE);
		}

		BigDecimal newBalance = wallet.currentBalance().add(amount.multiply(BigDecimal.valueOf(type.balanceSign())));
		if (newBalance.signum() < 0) {
			throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
		}

		try {
			LedgerEntryRecord entry = this.walletRepository.insertLedgerEntry(walletId, type, amount, reference,
					idempotencyKey, paymentTransactionId);
			if (type.movesSettledBalance()) {
				this.walletRepository.updateBalance(walletId, newBalance);
			}
			return entry;
		}
		catch (DuplicateKeyException ex) {
			// A concurrent posting with the same idempotency key won the race; treat as idempotent.
			if (idempotencyKey != null) {
				return this.walletRepository.findLedgerEntryByIdempotencyKey(idempotencyKey)
						.orElseThrow(() -> ex);
			}
			throw ex;
		}
	}

	@Transactional
	public LedgerEntryRecord credit(UUID walletId, BigDecimal amount, String reference, String idempotencyKey,
			UUID paymentTransactionId) {
		return postLedgerEntry(walletId, LedgerEntryType.CREDIT, amount, reference, idempotencyKey, paymentTransactionId);
	}

	@Transactional
	public LedgerEntryRecord debit(UUID walletId, BigDecimal amount, String reference, String idempotencyKey,
			UUID paymentTransactionId) {
		return postLedgerEntry(walletId, LedgerEntryType.DEBIT, amount, reference, idempotencyKey, paymentTransactionId);
	}

	private WalletRecord requireWalletByUser(UUID userId) {
		return this.walletRepository.findByUserId(userId)
				.orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));
	}
}
