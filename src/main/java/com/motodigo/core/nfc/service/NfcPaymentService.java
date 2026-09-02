package com.motodigo.core.nfc.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.motodigo.core.nfc.config.NfcProperties;
import com.motodigo.core.nfc.dto.NfcEligibilityResponse;
import com.motodigo.core.nfc.dto.NfcTokenIssueRequest;
import com.motodigo.core.nfc.dto.NfcTokenResponse;
import com.motodigo.core.nfc.entity.NfcPaymentTokenRecord;
import com.motodigo.core.nfc.entity.NfcTokenStatus;
import com.motodigo.core.nfc.exception.NfcErrorCode;
import com.motodigo.core.nfc.exception.NfcException;
import com.motodigo.core.nfc.repository.NfcOfflineQuotaRepository;
import com.motodigo.core.nfc.repository.NfcPaymentTokenRepository;
import com.motodigo.core.payment.entity.PaymentTransactionRecord;
import com.motodigo.core.payment.repository.PaymentTransactionRepository;
import com.motodigo.core.payment.repository.ReceiptRepository;
import com.motodigo.core.wallet.entity.WalletRecord;
import com.motodigo.core.wallet.entity.WalletStatus;
import com.motodigo.core.wallet.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NfcPaymentService {

	private final NfcProperties nfcProperties;
	private final NfcTokenSigner tokenSigner;
	private final NfcPaymentTokenRepository tokenRepository;
	private final NfcOfflineQuotaRepository quotaRepository;
	private final WalletService walletService;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final ReceiptRepository receiptRepository;
	private final Clock clock;

	@Autowired
	public NfcPaymentService(NfcProperties nfcProperties, NfcTokenSigner tokenSigner,
			NfcPaymentTokenRepository tokenRepository, NfcOfflineQuotaRepository quotaRepository,
			WalletService walletService, PaymentTransactionRepository paymentTransactionRepository,
			ReceiptRepository receiptRepository) {
		this(nfcProperties, tokenSigner, tokenRepository, quotaRepository, walletService,
				paymentTransactionRepository, receiptRepository, Clock.systemUTC());
	}

	NfcPaymentService(NfcProperties nfcProperties, NfcTokenSigner tokenSigner,
			NfcPaymentTokenRepository tokenRepository, NfcOfflineQuotaRepository quotaRepository,
			WalletService walletService, PaymentTransactionRepository paymentTransactionRepository,
			ReceiptRepository receiptRepository, Clock clock) {
		this.nfcProperties = nfcProperties;
		this.tokenSigner = tokenSigner;
		this.tokenRepository = tokenRepository;
		this.quotaRepository = quotaRepository;
		this.walletService = walletService;
		this.paymentTransactionRepository = paymentTransactionRepository;
		this.receiptRepository = receiptRepository;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	@Transactional(readOnly = true)
	public NfcEligibilityResponse getEligibility(UUID userId) {
		WalletRecord wallet = this.walletService.requireWalletByUser(userId);
		List<String> reasons = new ArrayList<>();
		boolean walletActive = wallet.status() == WalletStatus.ACTIVE;
		if (!walletActive) {
			reasons.add("WALLET_NOT_ACTIVE");
		}
		BigDecimal available = wallet.availableBalance();
		if (available.signum() <= 0) {
			reasons.add("INSUFFICIENT_AVAILABLE_BALANCE");
		}
		BigDecimal max = this.nfcProperties.maxAmount();
		var quota = this.quotaRepository.findByUserId(userId).orElse(null);
		Instant now = Instant.now(this.clock);
		BigDecimal quotaRemaining = null;
		Instant quotaExpires = null;
		if (quota != null && quota.expiresAt().isAfter(now)) {
			quotaRemaining = quota.remainingAmount();
			quotaExpires = quota.expiresAt();
		}
		boolean eligible = reasons.isEmpty();
		return new NfcEligibilityResponse(eligible, walletActive, available, max, quotaRemaining, quotaExpires,
				List.copyOf(reasons));
	}

	@Transactional
	public NfcTokenResponse issueToken(UUID userId, UUID activeProfileId, NfcTokenIssueRequest request,
			String idempotencyKey) {
		if (request == null || request.amount() == null || request.amount().signum() <= 0) {
			throw new NfcException(NfcErrorCode.NFC_VALIDATION_FAILED);
		}
		if (request.currency() == null || !"XAF".equalsIgnoreCase(request.currency())) {
			throw new NfcException(NfcErrorCode.NFC_VALIDATION_FAILED, "Devise NFC doit etre XAF.");
		}
		if (request.amount().compareTo(this.nfcProperties.maxAmount()) > 0) {
			throw new NfcException(NfcErrorCode.NFC_NOT_ALLOWED, "Montant au-dessus du plafond NFC.");
		}

		WalletRecord wallet = this.walletService.requireWalletByUser(userId);
		if (wallet.status() != WalletStatus.ACTIVE) {
			throw new NfcException(NfcErrorCode.NFC_NOT_ALLOWED);
		}
		if (wallet.availableBalance().compareTo(request.amount()) < 0) {
			throw new NfcException(NfcErrorCode.NFC_INSUFFICIENT_BALANCE);
		}

		Instant now = Instant.now(this.clock);
		Instant expiresAt = now.plus(this.nfcProperties.ttl());
		UUID jti = UUID.randomUUID();
		var signed = this.tokenSigner.sign(jti, wallet.id(), request.amount(), "XAF", request.bookingId(),
				request.tripId(), request.receiverWalletId(), expiresAt);

		String holdKey = idempotencyKey == null ? "nfc-hold-" + jti : "nfc-hold-" + idempotencyKey;
		this.walletService.hold(wallet.id(), request.amount(), "NFC_HOLD:" + jti, holdKey, null);

		NfcPaymentTokenRecord token = this.tokenRepository.insert(wallet.id(), jti, signed.signedToken(),
				request.amount(), expiresAt, request.bookingId(), request.tripId(), userId,
				request.receiverWalletId(), signed.payloadJson());

		BigDecimal quota = this.nfcProperties.offlineQuotaAmount().min(wallet.availableBalance());
		this.quotaRepository.upsert(userId, wallet.id(), quota, now.plus(this.nfcProperties.offlineQuotaTtl()));

		return NfcTokenResponse.from(token);
	}

	@Transactional(readOnly = true)
	public NfcTokenResponse getToken(UUID userId, UUID tokenId) {
		NfcPaymentTokenRecord token = this.tokenRepository.findById(tokenId)
				.orElseThrow(() -> new NfcException(NfcErrorCode.NFC_TOKEN_NOT_FOUND));
		if (token.payerUserId() != null && !token.payerUserId().equals(userId)) {
			throw new NfcException(NfcErrorCode.NFC_ACCESS_DENIED);
		}
		return NfcTokenResponse.from(token);
	}

	/**
	 * Validates and settles an offline NFC token (CORE-NFC-001 / CORE-OFFLINE-001).
	 *
	 * @return conflict code if rejected without throwing (for sync batch), or null on success
	 */
	@Transactional
	public RegularizeResult regularize(String signedToken, BigDecimal expectedAmount, UUID expectedTripId,
			String idempotencyKey) {
		Map<String, Object> payload;
		try {
			payload = this.tokenSigner.verifyAndParse(signedToken);
		}
		catch (NfcException ex) {
			return RegularizeResult.conflict("INVALID_SIGNATURE", ex.getMessage());
		}

		UUID jti = UUID.fromString(String.valueOf(payload.get("jti")));
		NfcPaymentTokenRecord locked = this.tokenRepository.lockByJtiForUpdate(jti)
				.orElseGet(() -> this.tokenRepository.findBySignedToken(signedToken).orElse(null));
		if (locked == null) {
			return RegularizeResult.conflict("INVALID_SIGNATURE", "Jeton NFC inconnu.");
		}
		if (locked.status() == NfcTokenStatus.CONSUMED) {
			return RegularizeResult.conflict("TOKEN_ALREADY_CONSUMED", NfcErrorCode.NFC_TOKEN_ALREADY_CONSUMED.message());
		}

		Instant now = Instant.now(this.clock);
		if (locked.expiresAt().isBefore(now) || locked.status() == NfcTokenStatus.EXPIRED) {
			this.tokenRepository.updateStatus(locked.id(), NfcTokenStatus.EXPIRED, null, null);
			releaseHoldQuietly(locked);
			return RegularizeResult.conflict("TOKEN_EXPIRED", NfcErrorCode.NFC_TOKEN_EXPIRED.message());
		}

		BigDecimal amount = locked.amount();
		if (expectedAmount != null && expectedAmount.compareTo(amount) != 0) {
			this.tokenRepository.updateStatus(locked.id(), NfcTokenStatus.REJECTED, null, null);
			releaseHoldQuietly(locked);
			return RegularizeResult.conflict("AMOUNT_MISMATCH", NfcErrorCode.NFC_AMOUNT_MISMATCH.message());
		}
		if (expectedTripId != null && locked.tripId() != null && !expectedTripId.equals(locked.tripId())) {
			this.tokenRepository.updateStatus(locked.id(), NfcTokenStatus.REJECTED, null, null);
			releaseHoldQuietly(locked);
			return RegularizeResult.conflict("TRIP_MISMATCH", NfcErrorCode.NFC_TRIP_MISMATCH.message());
		}

		String payKey = idempotencyKey == null ? "nfc-pay-" + jti : idempotencyKey;
		var existingPay = this.paymentTransactionRepository.findByIdempotencyKey(payKey);
		if (existingPay.isPresent() && existingPay.get().status().name().equals("CONFIRMED")) {
			return RegularizeResult.synced(locked.id(), existingPay.get().id());
		}

		PaymentTransactionRecord tx = existingPay.orElseGet(() -> this.paymentTransactionRepository.insertNfcPayment(
				locked.walletId(), locked.receiverWalletId(), locked.bookingId(), locked.tripId(), amount, payKey,
				locked.payloadJson()));

		String releaseKey = "nfc-release-" + jti;
		String debitKey = "nfc-debit-" + jti;
		this.walletService.releaseHoldAndDebit(locked.walletId(), amount, "NFC:" + jti, releaseKey, debitKey, tx.id());

		if (locked.receiverWalletId() != null) {
			this.walletService.credit(locked.receiverWalletId(), amount, "NFC_CREDIT:" + jti, "nfc-credit-" + jti,
					tx.id());
		}

		PaymentTransactionRecord confirmed = this.paymentTransactionRepository.markNfcConfirmed(tx.id());
		String receiptNumber = "MDG-" + LocalDate.now(this.clock.withZone(ZoneOffset.UTC)) + "-"
				+ confirmed.id().toString().substring(0, 8).toUpperCase(Locale.ROOT);
		this.receiptRepository.insert(confirmed.id(), receiptNumber, confirmed.amount(), confirmed.currency(),
				"pending://receipt/" + confirmed.id());

		NfcPaymentTokenRecord consumed = this.tokenRepository.updateStatus(locked.id(), NfcTokenStatus.CONSUMED, now,
				confirmed.id());
		return RegularizeResult.synced(consumed.id(), confirmed.id());
	}

	private void releaseHoldQuietly(NfcPaymentTokenRecord token) {
		try {
			this.walletService.release(token.walletId(), token.amount(), "NFC_RELEASE_FAIL:" + token.jti(),
					"nfc-release-fail-" + token.jti(), null);
		}
		catch (Exception ignored) {
			// Hold may already have been released.
		}
	}

	public record RegularizeResult(boolean success, String conflictCode, String message, UUID tokenId,
			UUID paymentTransactionId) {
		public static RegularizeResult synced(UUID tokenId, UUID paymentId) {
			return new RegularizeResult(true, null, null, tokenId, paymentId);
		}

		public static RegularizeResult conflict(String code, String message) {
			return new RegularizeResult(false, code, message, null, null);
		}
	}
}
