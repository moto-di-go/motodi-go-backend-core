package com.motodigo.core.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.motodigo.core.booking.dto.BookingResponse;
import com.motodigo.core.booking.dto.PayableBooking;
import com.motodigo.core.booking.entity.BookingRecord;
import com.motodigo.core.booking.service.BookingService;
import com.motodigo.core.ops.repository.AdminOpsRepository;
import com.motodigo.core.ops.service.AdminOpsService;
import com.motodigo.core.payment.client.MobileMoneyClient;
import com.motodigo.core.payment.client.MobileMoneyCollectionRequest;
import com.motodigo.core.payment.client.MobileMoneyCollectionResult;
import com.motodigo.core.payment.config.PaymentProperties;
import com.motodigo.core.payment.dto.AdminPaymentTransactionPageResponse;
import com.motodigo.core.payment.dto.DepositRequest;
import com.motodigo.core.payment.dto.DepositResponse;
import com.motodigo.core.payment.dto.MobileMoneyCallbackRequest;
import com.motodigo.core.payment.dto.RidePaymentResponse;
import com.motodigo.core.payment.entity.PaymentProvider;
import com.motodigo.core.payment.entity.PaymentStatus;
import com.motodigo.core.payment.entity.PaymentTransactionRecord;
import com.motodigo.core.payment.entity.PaymentType;
import com.motodigo.core.payment.entity.ReceiptRecord;
import com.motodigo.core.payment.exception.PaymentErrorCode;
import com.motodigo.core.payment.exception.PaymentException;
import com.motodigo.core.payment.repository.PaymentTransactionRepository;
import com.motodigo.core.payment.repository.ReceiptRepository;
import com.motodigo.core.tracking.service.TrackingService;
import com.motodigo.core.wallet.WalletProvisioningRepository;
import com.motodigo.core.wallet.entity.WalletRecord;
import com.motodigo.core.wallet.entity.WalletStatus;
import com.motodigo.core.wallet.exception.WalletErrorCode;
import com.motodigo.core.wallet.exception.WalletException;
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
	private final ReceiptRepository receiptRepository;
	private final WalletRepository walletRepository;
	private final WalletProvisioningRepository walletProvisioningRepository;
	private final WalletService walletService;
	private final BookingService bookingService;
	private final TrackingService trackingService;
	private final MobileMoneyClient mobileMoneyClient;
	private final PaymentProperties paymentProperties;
	private final AdminOpsRepository adminOpsRepository;
	private final Clock clock;

	@Autowired
	public PaymentService(PaymentTransactionRepository paymentTransactionRepository,
			ReceiptRepository receiptRepository,
			WalletRepository walletRepository,
			WalletProvisioningRepository walletProvisioningRepository,
			WalletService walletService,
			BookingService bookingService,
			TrackingService trackingService,
			MobileMoneyClient mobileMoneyClient,
			PaymentProperties paymentProperties,
			AdminOpsRepository adminOpsRepository) {
		this(paymentTransactionRepository, receiptRepository, walletRepository, walletProvisioningRepository,
				walletService, bookingService, trackingService, mobileMoneyClient, paymentProperties,
				adminOpsRepository, Clock.systemUTC());
	}

	PaymentService(PaymentTransactionRepository paymentTransactionRepository,
			ReceiptRepository receiptRepository,
			WalletRepository walletRepository,
			WalletProvisioningRepository walletProvisioningRepository,
			WalletService walletService,
			BookingService bookingService,
			TrackingService trackingService,
			MobileMoneyClient mobileMoneyClient,
			PaymentProperties paymentProperties,
			Clock clock) {
		this(paymentTransactionRepository, receiptRepository, walletRepository, walletProvisioningRepository,
				walletService, bookingService, trackingService, mobileMoneyClient, paymentProperties, null, clock);
	}

	PaymentService(PaymentTransactionRepository paymentTransactionRepository,
			ReceiptRepository receiptRepository,
			WalletRepository walletRepository,
			WalletProvisioningRepository walletProvisioningRepository,
			WalletService walletService,
			BookingService bookingService,
			TrackingService trackingService,
			MobileMoneyClient mobileMoneyClient,
			PaymentProperties paymentProperties,
			AdminOpsRepository adminOpsRepository,
			Clock clock) {
		this.paymentTransactionRepository = paymentTransactionRepository;
		this.receiptRepository = receiptRepository;
		this.walletRepository = walletRepository;
		this.walletProvisioningRepository = walletProvisioningRepository;
		this.walletService = walletService;
		this.bookingService = bookingService;
		this.trackingService = trackingService;
		this.mobileMoneyClient = mobileMoneyClient;
		this.paymentProperties = paymentProperties;
		this.adminOpsRepository = adminOpsRepository;
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

	/**
	 * Atomically pays a {@code PENDING_PAYMENT} booking from the passenger wallet (CORE-PAY-002).
	 *
	 * <p>Debits the passenger, credits the transporter (minus platform fee), marks the booking
	 * {@code PAID}, confirms the ride payment and issues a receipt. Insufficient balance fails
	 * without mutating the booking (transaction rollback).
	 */
	@Transactional
	public RidePaymentResponse payBooking(UUID userId, UUID activeProfileId, UUID bookingId, String idempotencyKey) {
		String normalizedKey = blankToNull(idempotencyKey);
		if (normalizedKey != null) {
			var existing = this.paymentTransactionRepository.findByIdempotencyKey(normalizedKey);
			if (existing.isPresent()) {
				return toRidePaymentResponse(existing.get());
			}
		}

		PayableBooking payable = this.bookingService.getPayableBooking(userId, activeProfileId, bookingId);
		WalletRecord passengerWallet = requireActiveWallet(userId);
		if (passengerWallet.currentBalance().compareTo(payable.amount()) < 0) {
			throw new PaymentException(PaymentErrorCode.INSUFFICIENT_BALANCE);
		}

		WalletRecord transporterWallet = requireActiveWallet(payable.transporterUserId());
		BigDecimal platformFee = computePlatformFee(payable.amount());
		BigDecimal transporterAmount = payable.amount().subtract(platformFee);

		PaymentTransactionRecord created;
		try {
			created = this.paymentTransactionRepository.insertRidePayment(
					payable.booking().id(),
					passengerWallet.id(),
					transporterWallet.id(),
					payable.amount(),
					platformFee,
					transporterAmount,
					normalizedKey,
					"{}");
		}
		catch (DuplicateKeyException ex) {
			if (normalizedKey != null) {
				return this.paymentTransactionRepository.findByIdempotencyKey(normalizedKey)
						.map(this::toRidePaymentResponse)
						.orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED));
			}
			throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		}

		try {
			this.walletService.debit(
					passengerWallet.id(),
					payable.amount(),
					"RIDE_PAYMENT:" + created.id(),
					"ride-debit:" + created.id(),
					created.id());
			this.walletService.credit(
					transporterWallet.id(),
					transporterAmount,
					"RIDE_PAYMENT:" + created.id(),
					"ride-credit:" + created.id(),
					created.id());
		}
		catch (WalletException ex) {
			if (ex.errorCode() == WalletErrorCode.INSUFFICIENT_BALANCE) {
				throw new PaymentException(PaymentErrorCode.INSUFFICIENT_BALANCE);
			}
			if (ex.errorCode() == WalletErrorCode.WALLET_NOT_ACTIVE) {
				throw new PaymentException(PaymentErrorCode.WALLET_NOT_ACTIVE);
			}
			throw new PaymentException(PaymentErrorCode.RIDE_PAYMENT_FAILED);
		}

		PaymentTransactionRecord confirmed = this.paymentTransactionRepository.markRideConfirmed(created.id());
		BookingRecord paidBooking = this.bookingService.markPaid(payable.booking().id());
		this.trackingService.createTripFromPaidBooking(paidBooking.id());
		paidBooking = this.bookingService.requireBookingById(paidBooking.id());
		ReceiptRecord receipt = issueReceipt(confirmed);

		queueNotification(confirmed, "Paiement confirme",
				"Votre paiement de " + confirmed.amount() + " " + confirmed.currency() + " a ete confirme.");
		queueNotificationForUser(payable.transporterUserId(), confirmed, "Paiement recu",
				"Vous avez recu " + transporterAmount + " " + confirmed.currency() + " pour une reservation.");

		return RidePaymentResponse.from(confirmed, receipt, BookingResponse.from(paidBooking));
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
		boolean revealSensitive = canRevealSensitive(adminAccess);
		List<DepositResponse> transactions = rows.stream()
				.limit(size)
				.map(tx -> maskDeposit(DepositResponse.from(tx), revealSensitive))
				.toList();
		return new AdminPaymentTransactionPageResponse(transactions, page, size, hasMore);
	}

	@Transactional(readOnly = true)
	public DepositResponse getForAdmin(AdminAccess adminAccess, UUID paymentId) {
		requireAdminReconcile(adminAccess);
		DepositResponse response = this.paymentTransactionRepository.findById(paymentId)
				.map(DepositResponse::from)
				.orElseThrow(() -> new PaymentException(PaymentErrorCode.DEPOSIT_NOT_FOUND));
		return maskDeposit(response, canRevealSensitive(adminAccess));
	}

	private boolean canRevealSensitive(AdminAccess adminAccess) {
		if (this.adminOpsRepository == null || adminAccess == null || adminAccess.actorProfileId() == null) {
			return false;
		}
		return this.adminOpsRepository.findAdminRole(adminAccess.actorProfileId())
				.map(AdminOpsService.FULL_ADMIN_ROLE::equals)
				.orElse(false);
	}

	private static DepositResponse maskDeposit(DepositResponse response, boolean revealSensitive) {
		return new DepositResponse(
				response.id(),
				response.status(),
				response.amount(),
				response.currency(),
				response.provider(),
				AdminOpsService.maskPhone(response.payerPhone(), revealSensitive),
				response.externalReference(),
				response.failureReason(),
				response.initiatedAt(),
				response.confirmedAt());
	}

	private RidePaymentResponse toRidePaymentResponse(PaymentTransactionRecord tx) {
		if (tx.type() != PaymentType.RIDE_PAYMENT) {
			throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		}
		ReceiptRecord receipt = this.receiptRepository.findByPaymentTransactionId(tx.id()).orElse(null);
		BookingResponse booking = tx.bookingId() == null ? null
				: BookingResponse.from(this.bookingService.requireBookingById(tx.bookingId()));
		return RidePaymentResponse.from(tx, receipt, booking);
	}

	private ReceiptRecord issueReceipt(PaymentTransactionRecord confirmed) {
		String receiptNumber = "MDG-" + LocalDate.now(this.clock.withZone(ZoneOffset.UTC))
				+ "-" + confirmed.id().toString().substring(0, 8).toUpperCase(Locale.ROOT);
		String pdfUrl = "pending://receipt/" + confirmed.id();
		return this.receiptRepository.insert(confirmed.id(), receiptNumber, confirmed.amount(), confirmed.currency(),
				pdfUrl);
	}

	private BigDecimal computePlatformFee(BigDecimal amount) {
		BigDecimal percent = this.paymentProperties.platformFeePercent();
		if (percent == null || percent.signum() <= 0) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}
		return amount.multiply(percent)
				.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
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
		queueNotificationForUser(userId, tx, title, body);
	}

	private void queueNotificationForUser(UUID userId, PaymentTransactionRecord tx, String title, String body) {
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
