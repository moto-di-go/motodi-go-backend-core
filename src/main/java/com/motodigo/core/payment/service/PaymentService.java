package com.motodigo.core.payment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.motodigo.core.payment.client.MobileMoneyClient;
import com.motodigo.core.payment.client.MobileMoneyCollectionRequest;
import com.motodigo.core.payment.client.MobileMoneyCollectionResult;
import com.motodigo.core.payment.config.PaymentProperties;
import com.motodigo.core.payment.dto.AdminPaymentTransactionPageResponse;
import com.motodigo.core.payment.dto.DepositRequest;
import com.motodigo.core.payment.dto.DepositResponse;
import com.motodigo.core.payment.dto.MobileMoneyCallbackRequest;
import com.motodigo.core.payment.entity.PaymentProvider;
import com.motodigo.core.payment.entity.PaymentStatus;
import com.motodigo.core.payment.entity.PaymentTransactionRecord;
import com.motodigo.core.payment.exception.PaymentErrorCode;
import com.motodigo.core.payment.exception.PaymentException;
import com.motodigo.core.payment.repository.PaymentTransactionRepository;
import com.motodigo.core.wallet.WalletProvisioningRepository;
import com.motodigo.core.wallet.entity.WalletRecord;
import com.motodigo.core.wallet.entity.WalletStatus;
import com.motodigo.core.wallet.repository.WalletRepository;
import com.motodigo.core.wallet.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PaymentService {

	public static final String ADMIN_RECONCILE_PERMISSION = "PAYMENT_RECONCILE_READ";

	private final PaymentTransactionRepository paymentTransactionRepository;
	private final WalletRepository walletRepository;
	private final WalletProvisioningRepository walletProvisioningRepository;
	private final WalletService walletService;
	private final MobileMoneyClient mobileMoneyClient;
	private final PaymentProperties paymentProperties;
	private final Clock clock;

	@Autowired
	public PaymentService(PaymentTransactionRepository paymentTransactionRepository,
			WalletRepository walletRepository,
			WalletProvisioningRepository walletProvisioningRepository,
			WalletService walletService,
			MobileMoneyClient mobileMoneyClient,
			PaymentProperties paymentProperties) {
		this(paymentTransactionRepository, walletRepository, walletProvisioningRepository, walletService,
				mobileMoneyClient, paymentProperties, Clock.systemUTC());
	}

	PaymentService(PaymentTransactionRepository paymentTransactionRepository,
			WalletRepository walletRepository,
			WalletProvisioningRepository walletProvisioningRepository,
			WalletService walletService,
			MobileMoneyClient mobileMoneyClient,
			PaymentProperties paymentProperties,
			Clock clock) {
		this.paymentTransactionRepository = paymentTransactionRepository;
		this.walletRepository = walletRepository;
		this.walletProvisioningRepository = walletProvisioningRepository;
		this.walletService = walletService;
		this.mobileMoneyClient = mobileMoneyClient;
		this.paymentProperties = paymentProperties;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	@Transactional(noRollbackFor = PaymentException.class)
	public DepositResponse initiateDeposit(UUID userId, DepositRequest request, String idempotencyKey) {
		validateDepositRequest(request);
		PaymentProvider provider;
		try {
			provider = PaymentProvider.forDeposit(request.provider());
		}
		catch (IllegalArgumentException ex) {
			throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		}

		String normalizedKey = blankToNull(idempotencyKey);
		if (normalizedKey != null) {
			var existing = this.paymentTransactionRepository.findByIdempotencyKey(normalizedKey);
			if (existing.isPresent()) {
				return DepositResponse.from(existing.get());
			}
		}

		WalletRecord wallet = requireActiveWallet(userId);
		PaymentTransactionRecord created;
		try {
			created = this.paymentTransactionRepository.insertDeposit(
					wallet.id(),
					request.amount(),
					request.currency().toUpperCase(Locale.ROOT),
					provider,
					request.phoneNumber(),
					normalizedKey,
					"{}");
		}
		catch (DuplicateKeyException ex) {
			if (normalizedKey != null) {
				return this.paymentTransactionRepository.findByIdempotencyKey(normalizedKey)
						.map(DepositResponse::from)
						.orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED));
			}
			throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		}

		String callbackUrl = "/api/v1/integrations/mobile-money/" + provider.callbackPathSegment() + "/callback";
		try {
			MobileMoneyCollectionResult collection = this.mobileMoneyClient.requestToPay(
					new MobileMoneyCollectionRequest(
							created.id(),
							provider,
							request.amount(),
							request.currency().toUpperCase(Locale.ROOT),
							request.phoneNumber(),
							callbackUrl));
			PaymentTransactionRecord pending = this.paymentTransactionRepository.markPendingCallback(
					created.id(), collection.externalReference());
			return DepositResponse.from(pending);
		}
		catch (PaymentException ex) {
			this.paymentTransactionRepository.markFailed(created.id(), ex.errorCode().message());
			throw ex;
		}
		catch (RuntimeException ex) {
			this.paymentTransactionRepository.markFailed(created.id(), PaymentErrorCode.DEPOSIT_INIT_FAILED.message());
			throw new PaymentException(PaymentErrorCode.DEPOSIT_INIT_FAILED);
		}
	}

	@Transactional
	public DepositResponse getDepositForUser(UUID userId, UUID depositId) {
		PaymentTransactionRecord tx = this.paymentTransactionRepository.findById(depositId)
				.orElseThrow(() -> new PaymentException(PaymentErrorCode.DEPOSIT_NOT_FOUND));
		UUID ownerUserId = this.paymentTransactionRepository.findWalletOwnerUserId(tx.passengerWalletId())
				.orElseThrow(() -> new PaymentException(PaymentErrorCode.DEPOSIT_NOT_FOUND));
		if (!ownerUserId.equals(userId)) {
			throw new PaymentException(PaymentErrorCode.DEPOSIT_NOT_FOUND);
		}
		return DepositResponse.from(expireIfNeeded(tx));
	}

	@Transactional
	public DepositResponse handleCallback(PaymentProvider provider, MobileMoneyCallbackRequest payload,
			String callbackSignature) {
		if (payload == null || blankToNull(payload.externalReference()) == null
				|| blankToNull(payload.status()) == null) {
			throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		}

		PaymentTransactionRecord locked = this.paymentTransactionRepository
				.lockByProviderAndExternalReference(provider, payload.externalReference().trim())
				.orElseThrow(() -> new PaymentException(PaymentErrorCode.CALLBACK_TRANSACTION_NOT_FOUND));

		if (locked.status().isTerminal()) {
			return DepositResponse.from(locked);
		}
		if (locked.status() != PaymentStatus.PENDING_CALLBACK) {
			throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		}

		String normalizedStatus = payload.status().trim().toUpperCase(Locale.ROOT);
		String signature = blankToNull(callbackSignature);
		return switch (normalizedStatus) {
			case "SUCCESS", "SUCCESSFUL", "CONFIRMED" -> confirmAndCredit(locked, signature);
			case "FAILED", "FAILURE", "REJECTED" -> failCallback(locked, PaymentStatus.FAILED,
					blankToNull(payload.failureReason()) == null ? "Depot refuse par l'operateur."
							: payload.failureReason().trim(),
					signature);
			case "EXPIRED", "TIMEOUT" -> failCallback(locked, PaymentStatus.EXPIRED,
					blankToNull(payload.failureReason()) == null ? "Depot expire."
							: payload.failureReason().trim(),
					signature);
			default -> throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		};
	}

	@Transactional(readOnly = true)
	public AdminPaymentTransactionPageResponse listForAdmin(AdminAccess adminAccess, PaymentProvider provider,
			PaymentStatus status, String externalReference, int page, int size) {
		requireAdminReconcile(adminAccess);
		List<PaymentTransactionRecord> rows = this.paymentTransactionRepository.findForAdmin(
				provider, status, externalReference, size + 1, page * size);
		boolean hasMore = rows.size() > size;
		List<DepositResponse> transactions = rows.stream().limit(size).map(DepositResponse::from).toList();
		return new AdminPaymentTransactionPageResponse(transactions, page, size, hasMore);
	}

	@Transactional(readOnly = true)
	public DepositResponse getForAdmin(AdminAccess adminAccess, UUID paymentId) {
		requireAdminReconcile(adminAccess);
		return this.paymentTransactionRepository.findById(paymentId)
				.map(DepositResponse::from)
				.orElseThrow(() -> new PaymentException(PaymentErrorCode.DEPOSIT_NOT_FOUND));
	}

	private DepositResponse confirmAndCredit(PaymentTransactionRecord locked, String signature) {
		this.walletService.credit(
				locked.passengerWalletId(),
				locked.amount(),
				"MOBILE_MONEY_DEPOSIT:" + locked.id(),
				"deposit:" + locked.id(),
				locked.id());
		PaymentTransactionRecord confirmed = this.paymentTransactionRepository.markConfirmed(locked.id(), signature);
		queueNotification(confirmed, "Depot confirme",
				"Votre depot de " + confirmed.amount() + " " + confirmed.currency() + " a ete credite.");
		return DepositResponse.from(confirmed);
	}

	private DepositResponse failCallback(PaymentTransactionRecord locked, PaymentStatus status, String reason,
			String signature) {
		PaymentTransactionRecord updated = this.paymentTransactionRepository.markCallbackFailure(
				locked.id(), status, reason, signature);
		String title = status == PaymentStatus.EXPIRED ? "Depot expire" : "Depot echoue";
		queueNotification(updated, title, reason);
		return DepositResponse.from(updated);
	}

	private void queueNotification(PaymentTransactionRecord tx, String title, String body) {
		UUID userId = this.paymentTransactionRepository.findWalletOwnerUserId(tx.passengerWalletId()).orElse(null);
		if (userId == null) {
			return;
		}
		String metadata = "{\"paymentTransactionId\":\"" + tx.id() + "\",\"status\":\"" + tx.status().name() + "\"}";
		this.paymentTransactionRepository.queuePaymentNotification(userId, tx.id(), title, body, metadata);
	}

	private PaymentTransactionRecord expireIfNeeded(PaymentTransactionRecord tx) {
		if (tx.status() != PaymentStatus.PENDING_CALLBACK) {
			return tx;
		}
		Instant deadline = tx.initiatedAt().plus(this.paymentProperties.mobileMoney().pendingTtl());
		if (!this.clock.instant().isAfter(deadline)) {
			return tx;
		}
		return this.paymentTransactionRepository
				.markExpired(tx.id(), "Depot expire faute de confirmation operateur.")
				.orElse(tx);
	}

	private WalletRecord requireActiveWallet(UUID userId) {
		this.walletProvisioningRepository.createActiveWalletIfMissing(userId);
		WalletRecord wallet = this.walletRepository.findByUserId(userId)
				.orElseThrow(() -> new PaymentException(PaymentErrorCode.DEPOSIT_INIT_FAILED));
		if (wallet.status() != WalletStatus.ACTIVE) {
			throw new PaymentException(PaymentErrorCode.WALLET_NOT_ACTIVE);
		}
		return wallet;
	}

	private static void validateDepositRequest(DepositRequest request) {
		if (request == null || request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0
				|| request.currency() == null || !"XAF".equalsIgnoreCase(request.currency())
				|| blankToNull(request.provider()) == null || blankToNull(request.phoneNumber()) == null) {
			throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		}
	}

	private static void requireAdminReconcile(AdminAccess adminAccess) {
		if (adminAccess == null || adminAccess.actorProfileId() == null
				|| !adminAccess.permissions().contains(ADMIN_RECONCILE_PERMISSION)) {
			throw new PaymentException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
		}
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	public record AdminAccess(
			UUID actorProfileId,
			String actorRole,
			List<String> permissions,
			String correlationId) {

		public AdminAccess {
			permissions = permissions == null ? List.of() : List.copyOf(permissions);
			actorRole = actorRole == null || actorRole.isBlank() ? "UNKNOWN" : actorRole;
		}
	}
}
