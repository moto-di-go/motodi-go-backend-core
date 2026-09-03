package com.motodigo.core.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.wallet.WalletProvisioningRepository;
import com.motodigo.core.wallet.entity.LedgerEntryRecord;
import com.motodigo.core.wallet.entity.LedgerEntryType;
import com.motodigo.core.wallet.entity.WalletRecord;
import com.motodigo.core.wallet.entity.WalletStatus;
import com.motodigo.core.wallet.exception.WalletErrorCode;
import com.motodigo.core.wallet.exception.WalletException;
import com.motodigo.core.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceTest {

	@Mock
	private WalletRepository walletRepository;

	@Mock
	private WalletProvisioningRepository walletProvisioningRepository;

	private WalletService walletService;

	private final UUID walletId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.walletService = new WalletService(this.walletRepository, this.walletProvisioningRepository);
	}

	@Test
	void creditLocksWalletAndIncreasesBalance() {
		lockedWallet(new BigDecimal("1000.00"), WalletStatus.ACTIVE);
		LedgerEntryRecord entry = ledgerEntry(LedgerEntryType.CREDIT, new BigDecimal("500.00"));
		when(this.walletRepository.insertLedgerEntry(eq(this.walletId), eq(LedgerEntryType.CREDIT),
				eq(new BigDecimal("500.00")), any(), any(), any())).thenReturn(entry);

		LedgerEntryRecord result = this.walletService.credit(this.walletId, new BigDecimal("500.00"), "depot", null, null);

		assertThat(result).isSameAs(entry);
		verify(this.walletRepository).updateBalance(this.walletId, new BigDecimal("1500.00"));
	}

	@Test
	void debitDecreasesBalance() {
		lockedWallet(new BigDecimal("1000.00"), WalletStatus.ACTIVE);
		when(this.walletRepository.insertLedgerEntry(eq(this.walletId), eq(LedgerEntryType.DEBIT),
				eq(new BigDecimal("300.00")), any(), any(), any()))
				.thenReturn(ledgerEntry(LedgerEntryType.DEBIT, new BigDecimal("300.00")));

		this.walletService.debit(this.walletId, new BigDecimal("300.00"), "paiement", null, null);

		verify(this.walletRepository).updateBalance(this.walletId, new BigDecimal("700.00"));
	}

	@Test
	void debitBeyondBalanceIsRefusedAndDoesNotMutate() {
		lockedWallet(new BigDecimal("100.00"), WalletStatus.ACTIVE);

		assertThatThrownBy(() -> this.walletService.debit(this.walletId, new BigDecimal("500.00"), "paiement", null, null))
				.isInstanceOf(WalletException.class)
				.extracting(ex -> ((WalletException) ex).errorCode())
				.isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE);

		verify(this.walletRepository, never()).insertLedgerEntry(any(), any(), any(), any(), any(), any());
		verify(this.walletRepository, never()).updateBalance(any(), any());
	}

	@Test
	void idempotentReplayReturnsExistingEntryWithoutPosting() {
		LedgerEntryRecord existing = ledgerEntry(LedgerEntryType.CREDIT, new BigDecimal("500.00"));
		when(this.walletRepository.findLedgerEntryByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

		LedgerEntryRecord result = this.walletService.credit(this.walletId, new BigDecimal("500.00"), "depot", "key-1", null);

		assertThat(result).isSameAs(existing);
		verify(this.walletRepository, never()).lockByIdForUpdate(any());
		verify(this.walletRepository, never()).insertLedgerEntry(any(), any(), any(), any(), any(), any());
		verify(this.walletRepository, never()).updateBalance(any(), any());
	}

	@Test
	void concurrentDuplicateKeyResolvesToPersistedEntry() {
		lockedWallet(new BigDecimal("1000.00"), WalletStatus.ACTIVE);
		LedgerEntryRecord persisted = ledgerEntry(LedgerEntryType.CREDIT, new BigDecimal("500.00"));
		when(this.walletRepository.findLedgerEntryByIdempotencyKey("key-2"))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(persisted));
		when(this.walletRepository.insertLedgerEntry(eq(this.walletId), eq(LedgerEntryType.CREDIT),
				eq(new BigDecimal("500.00")), any(), eq("key-2"), any()))
				.thenThrow(new DuplicateKeyException("uq_ledger_entries_idempotency_key"));

		LedgerEntryRecord result = this.walletService.credit(this.walletId, new BigDecimal("500.00"), "depot", "key-2", null);

		assertThat(result).isSameAs(persisted);
		verify(this.walletRepository, never()).updateBalance(any(), any());
	}

	@Test
	void postingOnNonActiveWalletIsRefused() {
		lockedWallet(new BigDecimal("1000.00"), WalletStatus.FROZEN);

		assertThatThrownBy(() -> this.walletService.credit(this.walletId, new BigDecimal("100.00"), "depot", null, null))
				.isInstanceOf(WalletException.class)
				.extracting(ex -> ((WalletException) ex).errorCode())
				.isEqualTo(WalletErrorCode.WALLET_NOT_ACTIVE);

		verify(this.walletRepository, never()).insertLedgerEntry(any(), any(), any(), any(), any(), any());
	}

	@Test
	void nonPositiveAmountIsRejected() {
		assertThatThrownBy(() -> this.walletService.credit(this.walletId, BigDecimal.ZERO, "depot", null, null))
				.isInstanceOf(WalletException.class)
				.extracting(ex -> ((WalletException) ex).errorCode())
				.isEqualTo(WalletErrorCode.WALLET_VALIDATION_FAILED);
	}

	@Test
	void getWalletEnsuresProvisioningThenReturnsWallet() {
		UUID userId = UUID.randomUUID();
		WalletRecord wallet = new WalletRecord(this.walletId, userId, "XAF", BigDecimal.ZERO, BigDecimal.ZERO,
				WalletStatus.ACTIVE, Instant.now(), Instant.now());
		when(this.walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

		var response = this.walletService.getWallet(userId);

		verify(this.walletProvisioningRepository).createActiveWalletIfMissing(userId);
		assertThat(response.id()).isEqualTo(this.walletId);
		assertThat(response.currency()).isEqualTo("XAF");
	}

	@Test
	void getWalletThrowsWhenStillMissingAfterEnsure() {
		UUID userId = UUID.randomUUID();
		when(this.walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.walletService.getWallet(userId))
				.isInstanceOf(WalletException.class)
				.extracting(ex -> ((WalletException) ex).errorCode())
				.isEqualTo(WalletErrorCode.WALLET_NOT_FOUND);

		verify(this.walletProvisioningRepository).createActiveWalletIfMissing(userId);
	}

	private void lockedWallet(BigDecimal balance, WalletStatus status) {
		when(this.walletRepository.lockByIdForUpdate(this.walletId))
				.thenReturn(Optional.of(new WalletRecord(this.walletId, UUID.randomUUID(), "XAF", balance,
						BigDecimal.ZERO, status, Instant.now(), Instant.now())));
	}

	private LedgerEntryRecord ledgerEntry(LedgerEntryType type, BigDecimal amount) {
		return new LedgerEntryRecord(UUID.randomUUID(), this.walletId, null, type, amount, "XAF", "ref",
				Instant.now(), null);
	}
}
