package com.motodigo.core.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.booking.dto.PayableBooking;
import com.motodigo.core.booking.entity.BookingRecord;
import com.motodigo.core.booking.entity.BookingStatus;
import com.motodigo.core.booking.exception.BookingErrorCode;
import com.motodigo.core.booking.exception.BookingException;
import com.motodigo.core.booking.service.BookingService;
import com.motodigo.core.intercity.entity.AnnouncementStatus;
import com.motodigo.core.intercity.entity.IntercityAnnouncementRecord;
import com.motodigo.core.payment.client.MobileMoneyClient;
import com.motodigo.core.payment.client.MobileMoneyCollectionResult;
import com.motodigo.core.payment.config.PaymentProperties;
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
import com.motodigo.core.wallet.entity.LedgerEntryRecord;
import com.motodigo.core.wallet.entity.LedgerEntryType;
import com.motodigo.core.wallet.entity.WalletRecord;
import com.motodigo.core.wallet.entity.WalletStatus;
import com.motodigo.core.wallet.repository.WalletRepository;
import com.motodigo.core.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

	@Mock
	private PaymentTransactionRepository paymentTransactionRepository;
	@Mock
	private ReceiptRepository receiptRepository;
	@Mock
	private WalletRepository walletRepository;
	@Mock
	private WalletProvisioningRepository walletProvisioningRepository;
	@Mock
	private WalletService walletService;
	@Mock
	private BookingService bookingService;
	@Mock
	private TrackingService trackingService;
	@Mock
	private MobileMoneyClient mobileMoneyClient;

	private final Instant now = Instant.parse("2026-08-09T08:00:00Z");
	private final UUID userId = UUID.randomUUID();
	private final UUID transporterUserId = UUID.randomUUID();
	private final UUID walletId = UUID.randomUUID();
	private final UUID transporterWalletId = UUID.randomUUID();
	private final UUID paymentId = UUID.randomUUID();
	private final UUID bookingId = UUID.randomUUID();
	private final UUID announcementId = UUID.randomUUID();
	private final UUID passengerProfileId = UUID.randomUUID();
	private final UUID transporterProfileId = UUID.randomUUID();
	private final UUID activeProfileId = UUID.randomUUID();
	private final UUID receiptId = UUID.randomUUID();

	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		PaymentProperties properties = new PaymentProperties(new PaymentProperties.MobileMoney(
				"stub", Duration.ofMinutes(30), 3000, 5000, Map.of()), BigDecimal.ZERO);
		this.paymentService = new PaymentService(
				this.paymentTransactionRepository,
				this.receiptRepository,
				this.walletRepository,
				this.walletProvisioningRepository,
				this.walletService,
				this.bookingService,
				this.trackingService,
				this.mobileMoneyClient,
				properties,
				Clock.fixed(this.now, ZoneOffset.UTC));
	}

	@Test
	void initiateDepositCreatesPendingTransaction() {
		activePassengerWallet(new BigDecimal("0.00"));
		PaymentTransactionRecord initiated = deposit(PaymentStatus.INITIATED, null);
		PaymentTransactionRecord pending = deposit(PaymentStatus.PENDING_CALLBACK, "ext-1");
		when(this.paymentTransactionRepository.insertDeposit(eq(this.walletId), any(), eq("XAF"),
				eq(PaymentProvider.MTN_MOMO), eq("+237699001122"), eq("idem-1"), any()))
				.thenReturn(initiated);
		when(this.mobileMoneyClient.requestToPay(any())).thenReturn(new MobileMoneyCollectionResult("ext-1"));
		when(this.paymentTransactionRepository.markPendingCallback(this.paymentId, "ext-1")).thenReturn(pending);

		DepositResponse response = this.paymentService.initiateDeposit(this.userId,
				new DepositRequest(new BigDecimal("5000.00"), "XAF", "MTN_MOMO", "+237699001122"),
				"idem-1");

		assertThat(response.status()).isEqualTo("PENDING_CALLBACK");
		assertThat(response.externalReference()).isEqualTo("ext-1");
	}

	@Test
	void initiateDepositReturnsExistingForSameIdempotencyKey() {
		PaymentTransactionRecord existing = deposit(PaymentStatus.PENDING_CALLBACK, "ext-existing");
		when(this.paymentTransactionRepository.findByIdempotencyKey("idem-dup")).thenReturn(Optional.of(existing));

		DepositResponse response = this.paymentService.initiateDeposit(this.userId,
				new DepositRequest(new BigDecimal("5000.00"), "XAF", "MTN_MOMO", "+237699001122"),
				"idem-dup");

		assertThat(response.id()).isEqualTo(this.paymentId);
		verify(this.mobileMoneyClient, never()).requestToPay(any());
	}

	@Test
	void initiateDepositMarksFailedWhenProviderRefuses() {
		activePassengerWallet(new BigDecimal("0.00"));
		PaymentTransactionRecord initiated = deposit(PaymentStatus.INITIATED, null);
		when(this.paymentTransactionRepository.insertDeposit(any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(initiated);
		when(this.mobileMoneyClient.requestToPay(any()))
				.thenThrow(new PaymentException(PaymentErrorCode.DEPOSIT_INIT_FAILED));
		when(this.paymentTransactionRepository.markFailed(eq(this.paymentId), any()))
				.thenReturn(deposit(PaymentStatus.FAILED, null));

		assertThatThrownBy(() -> this.paymentService.initiateDeposit(this.userId,
				new DepositRequest(new BigDecimal("5000.00"), "XAF", "ORANGE_MONEY", "+237699001122"),
				null))
				.isInstanceOf(PaymentException.class)
				.extracting(ex -> ((PaymentException) ex).errorCode())
				.isEqualTo(PaymentErrorCode.DEPOSIT_INIT_FAILED);
		verify(this.paymentTransactionRepository).markFailed(eq(this.paymentId), any());
	}

	@Test
	void successfulCallbackCreditsWalletOnceAndIsIdempotentOnReplay() {
		PaymentTransactionRecord pending = deposit(PaymentStatus.PENDING_CALLBACK, "ext-ok");
		PaymentTransactionRecord confirmed = deposit(PaymentStatus.CONFIRMED, "ext-ok");
		when(this.paymentTransactionRepository.lockByProviderAndExternalReference(PaymentProvider.MTN_MOMO, "ext-ok"))
				.thenReturn(Optional.of(pending), Optional.of(confirmed));
		when(this.walletService.credit(eq(this.walletId), eq(new BigDecimal("5000.00")), any(),
				eq("deposit:" + this.paymentId), eq(this.paymentId)))
				.thenReturn(new LedgerEntryRecord(UUID.randomUUID(), this.walletId, this.paymentId,
						LedgerEntryType.CREDIT, new BigDecimal("5000.00"), "XAF", "ref", this.now,
						"deposit:" + this.paymentId));
		when(this.paymentTransactionRepository.markConfirmed(this.paymentId, "sig"))
				.thenReturn(confirmed);
		when(this.paymentTransactionRepository.findWalletOwnerUserId(this.walletId))
				.thenReturn(Optional.of(this.userId));

		DepositResponse first = this.paymentService.handleCallback(PaymentProvider.MTN_MOMO,
				new MobileMoneyCallbackRequest("ext-ok", "SUCCESS", null), "sig");
		DepositResponse second = this.paymentService.handleCallback(PaymentProvider.MTN_MOMO,
				new MobileMoneyCallbackRequest("ext-ok", "SUCCESS", null), "sig");

		assertThat(first.status()).isEqualTo("CONFIRMED");
		assertThat(second.status()).isEqualTo("CONFIRMED");
		verify(this.walletService).credit(eq(this.walletId), eq(new BigDecimal("5000.00")), any(),
				eq("deposit:" + this.paymentId), eq(this.paymentId));
		verify(this.paymentTransactionRepository).queuePaymentNotification(eq(this.userId), eq(this.paymentId), any(),
				any(), any());
	}

	@Test
	void failedCallbackDoesNotCredit() {
		PaymentTransactionRecord pending = deposit(PaymentStatus.PENDING_CALLBACK, "ext-fail");
		PaymentTransactionRecord failed = deposit(PaymentStatus.FAILED, "ext-fail");
		when(this.paymentTransactionRepository.lockByProviderAndExternalReference(PaymentProvider.ORANGE_MONEY,
				"ext-fail")).thenReturn(Optional.of(pending));
		when(this.paymentTransactionRepository.markCallbackFailure(eq(this.paymentId), eq(PaymentStatus.FAILED),
				any(), eq("sig"))).thenReturn(failed);
		when(this.paymentTransactionRepository.findWalletOwnerUserId(this.walletId))
				.thenReturn(Optional.of(this.userId));

		DepositResponse response = this.paymentService.handleCallback(PaymentProvider.ORANGE_MONEY,
				new MobileMoneyCallbackRequest("ext-fail", "FAILED", "insuffisant"), "sig");

		assertThat(response.status()).isEqualTo("FAILED");
		verify(this.walletService, never()).credit(any(), any(), any(), any(), any());
	}

	@Test
	void getDepositExpiresPendingAfterTtl() {
		PaymentTransactionRecord pending = new PaymentTransactionRecord(
				this.paymentId, PaymentType.MOBILE_MONEY_DEPOSIT, PaymentStatus.PENDING_CALLBACK,
				new BigDecimal("5000.00"), "XAF", "ext-exp", null, PaymentProvider.AGGREGATOR, "+237699001122",
				null, this.walletId, null, null, null, null, this.now.minus(Duration.ofMinutes(45)), null, null, "{}");
		PaymentTransactionRecord expired = deposit(PaymentStatus.EXPIRED, "ext-exp");
		when(this.paymentTransactionRepository.findById(this.paymentId)).thenReturn(Optional.of(pending));
		when(this.paymentTransactionRepository.findWalletOwnerUserId(this.walletId))
				.thenReturn(Optional.of(this.userId));
		when(this.paymentTransactionRepository.markExpired(eq(this.paymentId), any()))
				.thenReturn(Optional.of(expired));

		DepositResponse response = this.paymentService.getDepositForUser(this.userId, this.paymentId);

		assertThat(response.status()).isEqualTo("EXPIRED");
	}

	@Test
	void adminListRequiresPermission() {
		assertThatThrownBy(() -> this.paymentService.listForAdmin(
				new PaymentService.AdminAccess(UUID.randomUUID(), "ADMIN", List.of("KYC_REVIEW_READ"), "c"),
				null, null, null, 0, 20))
				.isInstanceOf(PaymentException.class)
				.extracting(ex -> ((PaymentException) ex).errorCode())
				.isEqualTo(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
	}

	@Test
	void adminListWithPermissionReturnsPage() {
		when(this.paymentTransactionRepository.findForAdmin(eq(PaymentProvider.MTN_MOMO), isNull(), eq("ext-1"),
				eq(21), eq(0))).thenReturn(List.of(deposit(PaymentStatus.CONFIRMED, "ext-1")));

		var page = this.paymentService.listForAdmin(
				new PaymentService.AdminAccess(UUID.randomUUID(), "ADMIN",
						List.of(PaymentService.ADMIN_RECONCILE_PERMISSION), "c"),
				PaymentProvider.MTN_MOMO, null, "ext-1", 0, 20);

		assertThat(page.transactions()).hasSize(1);
		assertThat(page.transactions().getFirst().externalReference()).isEqualTo("ext-1");
		assertThat(page.hasMore()).isFalse();
	}

	@Test
	void payBookingHappyPathDebitsCreditsMarksPaidAndIssuesReceipt() {
		BigDecimal amount = new BigDecimal("18000.00");
		PayableBooking payable = payableBooking(amount, BookingStatus.PENDING_PAYMENT);
		when(this.bookingService.getPayableBooking(this.userId, this.activeProfileId, this.bookingId))
				.thenReturn(payable);
		activePassengerWallet(new BigDecimal("20000.00"));
		activeTransporterWallet();

		PaymentTransactionRecord initiated = ridePayment(PaymentStatus.INITIATED, amount);
		PaymentTransactionRecord confirmed = ridePayment(PaymentStatus.CONFIRMED, amount);
		BookingRecord paidBooking = booking(BookingStatus.PAID);
		ReceiptRecord receipt = new ReceiptRecord(this.receiptId, this.paymentId, "MDG-20260809-ABCDEF12",
				amount, "XAF", this.now, "pending://receipt/" + this.paymentId);

		when(this.paymentTransactionRepository.insertRidePayment(eq(this.bookingId), eq(this.walletId),
				eq(this.transporterWalletId), eq(amount), eq(new BigDecimal("0.00")), eq(amount), eq("pay-1"), any()))
				.thenReturn(initiated);
		when(this.walletService.debit(eq(this.walletId), eq(amount), any(), eq("ride-debit:" + this.paymentId),
				eq(this.paymentId)))
				.thenReturn(ledger(LedgerEntryType.DEBIT, amount));
		when(this.walletService.credit(eq(this.transporterWalletId), eq(amount), any(),
				eq("ride-credit:" + this.paymentId), eq(this.paymentId)))
				.thenReturn(ledger(LedgerEntryType.CREDIT, amount));
		when(this.paymentTransactionRepository.markRideConfirmed(this.paymentId)).thenReturn(confirmed);
		when(this.bookingService.markPaid(this.bookingId)).thenReturn(paidBooking);
		when(this.bookingService.requireBookingById(this.bookingId)).thenReturn(paidBooking);
		when(this.receiptRepository.insert(eq(this.paymentId), any(), eq(amount), eq("XAF"), any()))
				.thenReturn(receipt);
		when(this.paymentTransactionRepository.findWalletOwnerUserId(this.walletId))
				.thenReturn(Optional.of(this.userId));

		RidePaymentResponse response = this.paymentService.payBooking(this.userId, this.activeProfileId,
				this.bookingId, "pay-1");

		assertThat(response.status()).isEqualTo("CONFIRMED");
		assertThat(response.amount()).isEqualByComparingTo(amount);
		assertThat(response.transporterAmount()).isEqualByComparingTo(amount);
		assertThat(response.platformFee()).isEqualByComparingTo("0.00");
		assertThat(response.booking().status()).isEqualTo("PAID");
		assertThat(response.receiptId()).isEqualTo(this.receiptId);
		verify(this.bookingService).markPaid(this.bookingId);
		verify(this.trackingService).createTripFromPaidBooking(this.bookingId);
		verify(this.walletService).debit(eq(this.walletId), eq(amount), any(), any(), eq(this.paymentId));
		verify(this.walletService).credit(eq(this.transporterWalletId), eq(amount), any(), any(), eq(this.paymentId));
	}

	@Test
	void payBookingRefusesInsufficientBalanceWithoutMarkingPaid() {
		BigDecimal amount = new BigDecimal("18000.00");
		when(this.bookingService.getPayableBooking(this.userId, this.activeProfileId, this.bookingId))
				.thenReturn(payableBooking(amount, BookingStatus.PENDING_PAYMENT));
		activePassengerWallet(new BigDecimal("100.00"));

		assertThatThrownBy(() -> this.paymentService.payBooking(this.userId, this.activeProfileId, this.bookingId,
				"pay-low"))
				.isInstanceOf(PaymentException.class)
				.extracting(ex -> ((PaymentException) ex).errorCode())
				.isEqualTo(PaymentErrorCode.INSUFFICIENT_BALANCE);

		verify(this.paymentTransactionRepository, never()).insertRidePayment(any(), any(), any(), any(), any(), any(),
				any(), any());
		verify(this.bookingService, never()).markPaid(any());
		verify(this.walletService, never()).debit(any(), any(), any(), any(), any());
	}

	@Test
	void payBookingRefusesWrongBookingStatus() {
		when(this.bookingService.getPayableBooking(this.userId, this.activeProfileId, this.bookingId))
				.thenThrow(new BookingException(BookingErrorCode.BOOKING_NOT_PAYABLE));

		assertThatThrownBy(() -> this.paymentService.payBooking(this.userId, this.activeProfileId, this.bookingId,
				null))
				.isInstanceOf(BookingException.class)
				.extracting(ex -> ((BookingException) ex).errorCode())
				.isEqualTo(BookingErrorCode.BOOKING_NOT_PAYABLE);

		verify(this.walletService, never()).debit(any(), any(), any(), any(), any());
		verify(this.bookingService, never()).markPaid(any());
	}

	private void activePassengerWallet(BigDecimal balance) {
		when(this.walletRepository.findByUserId(this.userId)).thenReturn(Optional.of(new WalletRecord(
				this.walletId, this.userId, "XAF", balance, WalletStatus.ACTIVE, this.now, this.now)));
	}

	private void activeTransporterWallet() {
		when(this.walletRepository.findByUserId(this.transporterUserId)).thenReturn(Optional.of(new WalletRecord(
				this.transporterWalletId, this.transporterUserId, "XAF", BigDecimal.ZERO, WalletStatus.ACTIVE,
				this.now, this.now)));
	}

	private PayableBooking payableBooking(BigDecimal amount, BookingStatus status) {
		BookingRecord booking = booking(status);
		IntercityAnnouncementRecord announcement = new IntercityAnnouncementRecord(
				this.announcementId, this.transporterProfileId, UUID.randomUUID(), null,
				"Yaounde", "Douala", "Mvan", LocalDate.of(2026, 8, 20), LocalTime.of(8, 0),
				new BigDecimal("9000.00"), 4, 2, null, AnnouncementStatus.PUBLISHED, this.now, this.now);
		return new PayableBooking(booking, announcement, amount, this.transporterUserId);
	}

	private BookingRecord booking(BookingStatus status) {
		return new BookingRecord(
				this.bookingId, this.announcementId, this.passengerProfileId, null, status, 2,
				"+237670000001", this.now, this.now, null, this.now.plus(Duration.ofMinutes(10)), null);
	}

	private PaymentTransactionRecord deposit(PaymentStatus status, String externalReference) {
		return new PaymentTransactionRecord(
				this.paymentId,
				PaymentType.MOBILE_MONEY_DEPOSIT,
				status,
				new BigDecimal("5000.00"),
				"XAF",
				externalReference,
				"idem-1",
				PaymentProvider.MTN_MOMO,
				"+237699001122",
				null,
				this.walletId,
				null,
				null,
				null,
				null,
				this.now,
				status == PaymentStatus.CONFIRMED ? this.now : null,
				status == PaymentStatus.FAILED || status == PaymentStatus.EXPIRED ? "reason" : null,
				"{}");
	}

	private PaymentTransactionRecord ridePayment(PaymentStatus status, BigDecimal amount) {
		return new PaymentTransactionRecord(
				this.paymentId,
				PaymentType.RIDE_PAYMENT,
				status,
				amount,
				"XAF",
				null,
				"pay-1",
				PaymentProvider.WALLET,
				null,
				null,
				this.walletId,
				this.transporterWalletId,
				this.bookingId,
				new BigDecimal("0.00"),
				amount,
				this.now,
				status == PaymentStatus.CONFIRMED ? this.now : null,
				null,
				"{}");
	}

	private LedgerEntryRecord ledger(LedgerEntryType type, BigDecimal amount) {
		return new LedgerEntryRecord(UUID.randomUUID(), this.walletId, this.paymentId, type, amount, "XAF", "ref",
				this.now, "key");
	}
}
