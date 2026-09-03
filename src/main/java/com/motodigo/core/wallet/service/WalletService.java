package com.motodigo.core.wallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.wallet.WalletProvisioningRepository;
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
 * Wallet balance and accounting ledger (CORE-WALLET-001 / CORE-NFC-001 holds).
 */
@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class WalletService {

	private final WalletRepository walletRepository;
	private final WalletProvisioningRepository walletProvisioningRepository;

	public WalletService(WalletRepository walletRepository,
			WalletProvisioningRepository walletProvisioningRepository) {
		this.walletRepository = walletRepository;
		this.walletProvisioningRepository = walletProvisioningRepository;
	}

	@Transactional
	public WalletResponse getWallet(UUID userId) {
		return WalletResponse.from(requireWalletByUser(userId));
	}

	@Transactional
	public LedgerPageResponse getLedger(UUID userId, int page, int size) {
		WalletRecord wallet = requireWalletByUser(userId);
		List<LedgerEntryRecord> rows = this.walletRepository.findLedger(wallet.id(), size + 1, page * size);
		boolean hasMore = rows.size() > size;
		List<LedgerEntryResponse> entries = rows.stream()
				.limit(size)
				.map(LedgerEntryResponse::from)
				.toList();
		return new LedgerPageResponse(entries, page, size, hasMore);
	}

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

		BigDecimal held = wallet.heldBalance() == null ? BigDecimal.ZERO : wallet.heldBalance();
		BigDecimal newBalance = wallet.currentBalance().add(amount.multiply(BigDecimal.valueOf(type.balanceSign())));
		if (newBalance.signum() < 0) {
			throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
		}
		if (type == LedgerEntryType.HOLD) {
			BigDecimal available = wallet.currentBalance().subtract(held);
			if (available.compareTo(amount) < 0) {
				throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
			}
		}
		if (type == LedgerEntryType.RELEASE && held.compareTo(amount) < 0) {
			throw new WalletException(WalletErrorCode.WALLET_VALIDATION_FAILED);
		}

		try {
			LedgerEntryRecord entry = this.walletRepository.insertLedgerEntry(walletId, type, amount, reference,
					idempotencyKey, paymentTransactionId);
			if (type.movesSettledBalance()) {
				this.walletRepository.updateBalance(walletId, newBalance);
			}
			else if (type == LedgerEntryType.HOLD) {
				this.walletRepository.updateHeldBalance(walletId, held.add(amount));
			}
			else if (type == LedgerEntryType.RELEASE) {
				this.walletRepository.updateHeldBalance(walletId, held.subtract(amount));
			}
			return entry;
		}
		catch (DuplicateKeyException ex) {
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

	@Transactional
	public LedgerEntryRecord hold(UUID walletId, BigDecimal amount, String reference, String idempotencyKey,
			UUID paymentTransactionId) {
		return postLedgerEntry(walletId, LedgerEntryType.HOLD, amount, reference, idempotencyKey, paymentTransactionId);
	}

	@Transactional
	public LedgerEntryRecord release(UUID walletId, BigDecimal amount, String reference, String idempotencyKey,
			UUID paymentTransactionId) {
		return postLedgerEntry(walletId, LedgerEntryType.RELEASE, amount, reference, idempotencyKey,
				paymentTransactionId);
	}

	/**
	 * Releases an NFC hold then debits the settled balance in one locked transaction.
	 */
	@Transactional
	public void releaseHoldAndDebit(UUID walletId, BigDecimal amount, String reference, String releaseKey,
			String debitKey, UUID paymentTransactionId) {
		release(walletId, amount, reference + "-release", releaseKey, paymentTransactionId);
		debit(walletId, amount, reference + "-debit", debitKey, paymentTransactionId);
	}

	public WalletRecord requireWalletByUser(UUID userId) {
		this.walletProvisioningRepository.createActiveWalletIfMissing(userId);
		return this.walletRepository.findByUserId(userId)
				.orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));
	}
}
