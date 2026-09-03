package com.motodigo.core.nfc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.nfc.config.NfcProperties;
import com.motodigo.core.nfc.entity.NfcPaymentTokenRecord;
import com.motodigo.core.nfc.entity.NfcTokenStatus;
import com.motodigo.core.nfc.exception.NfcErrorCode;
import com.motodigo.core.nfc.exception.NfcException;
import com.motodigo.core.nfc.repository.NfcOfflineQuotaRepository;
import com.motodigo.core.nfc.repository.NfcPaymentTokenRepository;
import com.motodigo.core.payment.entity.PaymentProvider;
import com.motodigo.core.payment.entity.PaymentStatus;
import com.motodigo.core.payment.entity.PaymentTransactionRecord;
import com.motodigo.core.payment.entity.PaymentType;
import com.motodigo.core.payment.repository.PaymentTransactionRepository;
import com.motodigo.core.payment.repository.ReceiptRepository;
import com.motodigo.core.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NfcPaymentServiceTest {

	@Mock
	private NfcTokenSigner tokenSigner;
	@Mock
	private NfcPaymentTokenRepository tokenRepository;
	@Mock
	private NfcOfflineQuotaRepository quotaRepository;
	@Mock
	private WalletService walletService;
	@Mock
	private PaymentTransactionRepository paymentTransactionRepository;
	@Mock
	private ReceiptRepository receiptRepository;

	private NfcPaymentService service;
	private Instant now;
	private UUID walletId;
	private UUID jti;
	private UUID tokenId;

	@BeforeEach
	void setUp() {
		this.now = Instant.parse("2026-09-02T12:00:00Z");
		this.walletId = UUID.randomUUID();
		this.jti = UUID.randomUUID();
		this.tokenId = UUID.randomUUID();
		NfcProperties properties = new NfcProperties("test-secret-nfc-signing-key-32b",
				java.time.Duration.ofMinutes(5), new BigDecimal("50000"), new BigDecimal("20000"),
				java.time.Duration.ofHours(24));
		this.service = new NfcPaymentService(properties, this.tokenSigner, this.tokenRepository, this.quotaRepository,
				this.walletService, this.paymentTransactionRepository, this.receiptRepository,
				Clock.fixed(this.now, ZoneOffset.UTC));
	}

	@Test
	void regularizeHappyPathSettlesPayment() {
		String signed = "body.sig";
		when(this.tokenSigner.verifyAndParse(signed)).thenReturn(Map.of("jti", this.jti.toString()));
		NfcPaymentTokenRecord locked = token(NfcTokenStatus.ISSUED, this.now.plusSeconds(300));
		when(this.tokenRepository.lockByJtiForUpdate(this.jti)).thenReturn(Optional.of(locked));
		when(this.paymentTransactionRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
		PaymentTransactionRecord tx = payment(PaymentStatus.INITIATED);
		when(this.paymentTransactionRepository.insertNfcPayment(eq(this.walletId), isNull(), isNull(), isNull(),
				eq(new BigDecimal("1500.00")), eq("idem-1"), any())).thenReturn(tx);
		when(this.paymentTransactionRepository.markNfcConfirmed(tx.id())).thenReturn(payment(PaymentStatus.CONFIRMED));
		when(this.tokenRepository.updateStatus(eq(this.tokenId), eq(NfcTokenStatus.CONSUMED), eq(this.now), eq(tx.id())))
				.thenReturn(token(NfcTokenStatus.CONSUMED, this.now.plusSeconds(300)));

		var result = this.service.regularize(signed, new BigDecimal("1500.00"), null, "idem-1");

		assertThat(result.success()).isTrue();
		assertThat(result.tokenId()).isEqualTo(this.tokenId);
		verify(this.walletService).releaseHoldAndDebit(eq(this.walletId), eq(new BigDecimal("1500.00")), any(), any(),
				any(), eq(tx.id()));
		verify(this.receiptRepository).insert(eq(tx.id()), any(), eq(new BigDecimal("1500.00")), eq("XAF"), any());
	}

	@Test
	void regularizeExpiredReturnsConflict() {
		String signed = "body.sig";
		when(this.tokenSigner.verifyAndParse(signed)).thenReturn(Map.of("jti", this.jti.toString()));
		NfcPaymentTokenRecord locked = token(NfcTokenStatus.ISSUED, this.now.minusSeconds(10));
		when(this.tokenRepository.lockByJtiForUpdate(this.jti)).thenReturn(Optional.of(locked));
		when(this.tokenRepository.updateStatus(this.tokenId, NfcTokenStatus.EXPIRED, null, null))
				.thenReturn(token(NfcTokenStatus.EXPIRED, this.now.minusSeconds(10)));

		var result = this.service.regularize(signed, null, null, "idem-exp");

		assertThat(result.success()).isFalse();
		assertThat(result.conflictCode()).isEqualTo("TOKEN_EXPIRED");
		verify(this.paymentTransactionRepository, never()).insertNfcPayment(any(), any(), any(), any(), any(), any(),
				any());
	}

	@Test
	void regularizeAlreadyConsumedReturnsConflict() {
		String signed = "body.sig";
		when(this.tokenSigner.verifyAndParse(signed)).thenReturn(Map.of("jti", this.jti.toString()));
		when(this.tokenRepository.lockByJtiForUpdate(this.jti))
				.thenReturn(Optional.of(token(NfcTokenStatus.CONSUMED, this.now.plusSeconds(60))));

		var result = this.service.regularize(signed, null, null, "idem-dup");

		assertThat(result.success()).isFalse();
		assertThat(result.conflictCode()).isEqualTo("TOKEN_ALREADY_CONSUMED");
	}

	@Test
	void regularizeInvalidSignatureReturnsConflict() {
		when(this.tokenSigner.verifyAndParse("bad")).thenThrow(new NfcException(NfcErrorCode.NFC_INVALID_SIGNATURE));

		var result = this.service.regularize("bad", null, null, "idem-sig");

		assertThat(result.success()).isFalse();
		assertThat(result.conflictCode()).isEqualTo("INVALID_SIGNATURE");
	}

	private NfcPaymentTokenRecord token(NfcTokenStatus status, Instant expiresAt) {
		return new NfcPaymentTokenRecord(this.tokenId, this.walletId, null, "body.sig", new BigDecimal("1500.00"),
				this.now.minusSeconds(30), expiresAt, status == NfcTokenStatus.CONSUMED ? this.now : null, status,
				this.jti, null, null, UUID.randomUUID(), null, "{}");
	}

	private PaymentTransactionRecord payment(PaymentStatus status) {
		UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
		return new PaymentTransactionRecord(id, PaymentType.NFC_PAYMENT, status, new BigDecimal("1500.00"), "XAF",
				null, "idem-1", PaymentProvider.NFC, null, null, this.walletId, null, null, null, null, this.now,
				status == PaymentStatus.CONFIRMED ? this.now : null, null, "{}");
	}
}
