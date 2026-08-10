package com.motodigo.core.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.booking.config.BookingProperties;
import com.motodigo.core.booking.dto.CancelBookingRequest;
import com.motodigo.core.booking.dto.CreateBookingRequest;
import com.motodigo.core.booking.entity.BookingRecord;
import com.motodigo.core.booking.entity.BookingStatus;
import com.motodigo.core.booking.entity.SeatLockRecord;
import com.motodigo.core.booking.exception.BookingErrorCode;
import com.motodigo.core.booking.exception.BookingException;
import com.motodigo.core.booking.notify.BookingNotifier;
import com.motodigo.core.booking.notify.BookingNotifier.NotificationType;
import com.motodigo.core.booking.repository.AnnouncementSeatInventory;
import com.motodigo.core.booking.repository.BookingRepository;
import com.motodigo.core.booking.repository.SeatLockRepository;
import com.motodigo.core.intercity.entity.AnnouncementStatus;
import com.motodigo.core.intercity.entity.IntercityAnnouncementRecord;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import com.motodigo.core.profile.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

	private static final ZoneId ZONE = BookingService.APP_ZONE;
	private static final Instant FIXED_NOW = Instant.parse("2026-08-08T12:00:00Z");

	@Mock
	private BookingRepository bookingRepository;
	@Mock
	private SeatLockRepository seatLockRepository;
	@Mock
	private AnnouncementSeatInventory announcementSeatInventory;
	@Mock
	private ProfileRepository profileRepository;
	@Mock
	private BookingNotifier bookingNotifier;

	private BookingService service;
	private UUID passengerUserId;
	private UUID passengerProfileId;
	private UUID transporterUserId;
	private UUID transporterProfileId;
	private UUID announcementId;
	private UUID bookingId;

	@BeforeEach
	void setUp() {
		BookingProperties properties = new BookingProperties(Duration.ofMinutes(10), Duration.ofMinutes(10),
				"0 * * * * *");
		Clock clock = Clock.fixed(FIXED_NOW, ZONE);
		this.service = new BookingService(this.bookingRepository, this.seatLockRepository,
				this.announcementSeatInventory, this.profileRepository, this.bookingNotifier, properties, clock);
		this.passengerUserId = UUID.randomUUID();
		this.passengerProfileId = UUID.randomUUID();
		this.transporterUserId = UUID.randomUUID();
		this.transporterProfileId = UUID.randomUUID();
		this.announcementId = UUID.randomUUID();
		this.bookingId = UUID.randomUUID();
	}

	@Test
	void requestBookingRejectedWhenAnnouncementFull() {
		stubPassenger();
		when(this.announcementSeatInventory.findById(this.announcementId))
				.thenReturn(Optional.of(announcement(AnnouncementStatus.PUBLISHED, 4, 0)));

		assertThatThrownBy(() -> this.service.requestBooking(this.passengerUserId, this.passengerProfileId,
				this.announcementId, new CreateBookingRequest(1, "+237670000001")))
				.isInstanceOf(BookingException.class)
				.extracting(ex -> ((BookingException) ex).errorCode())
				.isEqualTo(BookingErrorCode.BOOKING_NOT_AVAILABLE);
	}

	@Test
	void requestBookingCreatesPendingAndNotifies() {
		stubPassenger();
		when(this.announcementSeatInventory.findById(this.announcementId))
				.thenReturn(Optional.of(announcement(AnnouncementStatus.PUBLISHED, 4, 4)));
		when(this.bookingRepository.existsActiveForPassengerAndAnnouncement(this.announcementId, this.passengerProfileId))
				.thenReturn(false);
		BookingRecord created = pendingBooking();
		when(this.bookingRepository.insert(eq(this.announcementId), eq(this.passengerProfileId), eq(1),
				eq("+237670000001"), any(Instant.class))).thenReturn(created);

		var response = this.service.requestBooking(this.passengerUserId, this.passengerProfileId, this.announcementId,
				new CreateBookingRequest(1, "+237670000001"));

		assertThat(response.status()).isEqualTo("PENDING");
		verify(this.bookingNotifier).notifyBooking(created, this.transporterProfileId, NotificationType.SUBSCRIPTION);
		verify(this.seatLockRepository, never()).insert(any(UUID.class), any(UUID.class), anyInt(), any(Instant.class));
	}

	@Test
	void confirmBookingLocksSeatsAndMovesToPendingPayment() {
		stubTransporter();
		BookingRecord pending = pendingBooking();
		when(this.bookingRepository.lockByIdForUpdate(this.bookingId)).thenReturn(Optional.of(pending));
		when(this.announcementSeatInventory.findById(this.announcementId))
				.thenReturn(Optional.of(announcement(AnnouncementStatus.PUBLISHED, 4, 4)));
		when(this.announcementSeatInventory.lockByIdForUpdate(this.announcementId))
				.thenReturn(Optional.of(announcement(AnnouncementStatus.PUBLISHED, 4, 4)));
		SeatLockRecord lock = new SeatLockRecord(UUID.randomUUID(), this.announcementId, this.bookingId, 1,
				FIXED_NOW, FIXED_NOW.plus(Duration.ofMinutes(10)), false);
		when(this.seatLockRepository.insert(eq(this.announcementId), eq(this.bookingId), eq(1), any(Instant.class)))
				.thenReturn(lock);
		when(this.announcementSeatInventory.decrementRemainingSeats(this.announcementId, 1))
				.thenReturn(announcement(AnnouncementStatus.PUBLISHED, 4, 3));
		BookingRecord confirmed = new BookingRecord(this.bookingId, this.announcementId, this.passengerProfileId, null,
				BookingStatus.PENDING_PAYMENT, 1, "+237670000001", FIXED_NOW, FIXED_NOW, null, lock.expiresAt(), null);
		when(this.bookingRepository.markPendingPayment(eq(this.bookingId), eq(FIXED_NOW), any(Instant.class)))
				.thenReturn(confirmed);

		var response = this.service.confirmBooking(this.transporterUserId, this.transporterProfileId, this.bookingId);

		assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
		verify(this.announcementSeatInventory).decrementRemainingSeats(this.announcementId, 1);
		verify(this.bookingNotifier).notifyBooking(confirmed, this.passengerProfileId, NotificationType.CONFIRMATION);
	}

	@Test
	void confirmBookingFailsWhenNoSeatsLeft() {
		stubTransporter();
		when(this.bookingRepository.lockByIdForUpdate(this.bookingId)).thenReturn(Optional.of(pendingBooking()));
		when(this.announcementSeatInventory.findById(this.announcementId))
				.thenReturn(Optional.of(announcement(AnnouncementStatus.PUBLISHED, 4, 0)));
		when(this.announcementSeatInventory.lockByIdForUpdate(this.announcementId))
				.thenReturn(Optional.of(announcement(AnnouncementStatus.PUBLISHED, 4, 0)));

		assertThatThrownBy(() -> this.service.confirmBooking(this.transporterUserId, this.transporterProfileId,
				this.bookingId))
				.isInstanceOf(BookingException.class)
				.extracting(ex -> ((BookingException) ex).errorCode())
				.isEqualTo(BookingErrorCode.NO_SEAT_AVAILABLE);
	}

	@Test
	void passengerCanCancelPendingWithoutSeatRelease() {
		stubPassenger();
		when(this.bookingRepository.lockByIdForUpdate(this.bookingId)).thenReturn(Optional.of(pendingBooking()));
		when(this.announcementSeatInventory.findById(this.announcementId))
				.thenReturn(Optional.of(announcement(AnnouncementStatus.PUBLISHED, 4, 4)));
		when(this.seatLockRepository.lockByBookingIdForUpdate(this.bookingId)).thenReturn(Optional.empty());
		BookingRecord cancelled = new BookingRecord(this.bookingId, this.announcementId, this.passengerProfileId, null,
				BookingStatus.CANCELLED, 1, "+237670000001", FIXED_NOW, null, FIXED_NOW, FIXED_NOW.plusSeconds(600), "Changement");
		when(this.bookingRepository.markCancelled(this.bookingId, "Changement", FIXED_NOW)).thenReturn(cancelled);

		var response = this.service.cancelBooking(this.passengerUserId, this.passengerProfileId, this.bookingId,
				new CancelBookingRequest("Changement"));

		assertThat(response.status()).isEqualTo("CANCELLED");
		verify(this.announcementSeatInventory, never()).incrementRemainingSeats(any(UUID.class), anyInt());
	}

	@Test
	void cancelPendingPaymentReleasesSeats() {
		stubPassenger();
		BookingRecord pendingPayment = new BookingRecord(this.bookingId, this.announcementId, this.passengerProfileId,
				null, BookingStatus.PENDING_PAYMENT, 1, "+237670000001", FIXED_NOW, FIXED_NOW, null,
				FIXED_NOW.plus(Duration.ofMinutes(10)), null);
		when(this.bookingRepository.lockByIdForUpdate(this.bookingId)).thenReturn(Optional.of(pendingPayment));
		when(this.announcementSeatInventory.findById(this.announcementId))
				.thenReturn(Optional.of(announcement(AnnouncementStatus.PUBLISHED, 4, 3)));
		SeatLockRecord lock = new SeatLockRecord(UUID.randomUUID(), this.announcementId, this.bookingId, 1,
				FIXED_NOW, FIXED_NOW.plus(Duration.ofMinutes(10)), false);
		when(this.seatLockRepository.lockByBookingIdForUpdate(this.bookingId)).thenReturn(Optional.of(lock));
		when(this.announcementSeatInventory.incrementRemainingSeats(this.announcementId, 1))
				.thenReturn(announcement(AnnouncementStatus.PUBLISHED, 4, 4));
		when(this.seatLockRepository.markConsumed(lock.id())).thenReturn(
				new SeatLockRecord(lock.id(), lock.announcementId(), lock.bookingId(), 1, lock.lockedAt(),
						lock.expiresAt(), true));
		BookingRecord cancelled = new BookingRecord(this.bookingId, this.announcementId, this.passengerProfileId, null,
				BookingStatus.CANCELLED, 1, "+237670000001", FIXED_NOW, FIXED_NOW, FIXED_NOW, lock.expiresAt(), "Annulation");
		when(this.bookingRepository.markCancelled(this.bookingId, "Annulation", FIXED_NOW)).thenReturn(cancelled);

		var response = this.service.cancelBooking(this.passengerUserId, this.passengerProfileId, this.bookingId,
				new CancelBookingRequest("Annulation"));

		assertThat(response.status()).isEqualTo("CANCELLED");
		verify(this.announcementSeatInventory).incrementRemainingSeats(this.announcementId, 1);
		verify(this.seatLockRepository).markConsumed(lock.id());
	}

	@Test
	void cancelPaidIsRejected() {
		stubPassenger();
		BookingRecord paid = new BookingRecord(this.bookingId, this.announcementId, this.passengerProfileId, null,
				BookingStatus.PAID, 1, "+237670000001", FIXED_NOW, FIXED_NOW, null, null, null);
		when(this.bookingRepository.lockByIdForUpdate(this.bookingId)).thenReturn(Optional.of(paid));
		when(this.announcementSeatInventory.findById(this.announcementId))
				.thenReturn(Optional.of(announcement(AnnouncementStatus.PUBLISHED, 4, 3)));

		assertThatThrownBy(() -> this.service.cancelBooking(this.passengerUserId, this.passengerProfileId,
				this.bookingId, new CancelBookingRequest("Trop tard")))
				.isInstanceOf(BookingException.class)
				.extracting(ex -> ((BookingException) ex).errorCode())
				.isEqualTo(BookingErrorCode.BOOKING_NOT_CANCELLABLE);
	}

	@Test
	void expireUnpaidBookingsMarksExpiredAndReleasesLocks() {
		BookingRecord pendingPayment = new BookingRecord(this.bookingId, this.announcementId, this.passengerProfileId,
				null, BookingStatus.PENDING_PAYMENT, 1, "+237670000001", FIXED_NOW.minusSeconds(1200), FIXED_NOW.minusSeconds(600),
				null, FIXED_NOW.minusSeconds(1), null);
		when(this.bookingRepository.findExpiredPendingOrPendingPayment(FIXED_NOW, 100))
				.thenReturn(List.of(pendingPayment));
		when(this.bookingRepository.lockByIdForUpdate(this.bookingId)).thenReturn(Optional.of(pendingPayment));
		SeatLockRecord lock = new SeatLockRecord(UUID.randomUUID(), this.announcementId, this.bookingId, 1,
				FIXED_NOW.minusSeconds(600), FIXED_NOW.minusSeconds(1), false);
		when(this.seatLockRepository.lockByBookingIdForUpdate(this.bookingId)).thenReturn(Optional.of(lock));
		when(this.announcementSeatInventory.incrementRemainingSeats(this.announcementId, 1))
				.thenReturn(announcement(AnnouncementStatus.PUBLISHED, 4, 4));
		when(this.seatLockRepository.markConsumed(lock.id())).thenReturn(
				new SeatLockRecord(lock.id(), lock.announcementId(), lock.bookingId(), 1, lock.lockedAt(),
						lock.expiresAt(), true));
		BookingRecord expired = new BookingRecord(this.bookingId, this.announcementId, this.passengerProfileId, null,
				BookingStatus.EXPIRED, 1, "+237670000001", pendingPayment.requestedAt(), pendingPayment.confirmedAt(), null,
				pendingPayment.expiresAt(), null);
		when(this.bookingRepository.markExpired(this.bookingId)).thenReturn(expired);

		int count = this.service.expireUnpaidBookings();

		assertThat(count).isEqualTo(1);
		verify(this.announcementSeatInventory).incrementRemainingSeats(this.announcementId, 1);
		verify(this.bookingNotifier).notifyBooking(expired, this.passengerProfileId, NotificationType.EXPIRATION);
	}

	@Test
	void getBookingVisibleToPassengerAndForbiddenToStranger() {
		stubPassenger();
		when(this.bookingRepository.findById(this.bookingId)).thenReturn(Optional.of(pendingBooking()));
		when(this.announcementSeatInventory.findById(this.announcementId))
				.thenReturn(Optional.of(announcement(AnnouncementStatus.PUBLISHED, 4, 4)));

		var response = this.service.getBooking(this.passengerUserId, this.passengerProfileId, this.bookingId);
		assertThat(response.id()).isEqualTo(this.bookingId);

		UUID strangerUser = UUID.randomUUID();
		UUID strangerProfile = UUID.randomUUID();
		when(this.profileRepository.findOwnedById(strangerUser, strangerProfile))
				.thenReturn(Optional.of(profile(strangerProfile, strangerUser, ProfileType.PASSENGER)));

		assertThatThrownBy(() -> this.service.getBooking(strangerUser, strangerProfile, this.bookingId))
				.isInstanceOf(BookingException.class)
				.extracting(ex -> ((BookingException) ex).errorCode())
				.isEqualTo(BookingErrorCode.BOOKING_FORBIDDEN);
	}

	private void stubPassenger() {
		when(this.profileRepository.findOwnedById(this.passengerUserId, this.passengerProfileId))
				.thenReturn(Optional.of(profile(this.passengerProfileId, this.passengerUserId, ProfileType.PASSENGER)));
	}

	private void stubTransporter() {
		when(this.profileRepository.findOwnedById(this.transporterUserId, this.transporterProfileId))
				.thenReturn(Optional.of(
						profile(this.transporterProfileId, this.transporterUserId, ProfileType.TRANSPORTER)));
	}

	private BookingRecord pendingBooking() {
		return new BookingRecord(this.bookingId, this.announcementId, this.passengerProfileId, null,
				BookingStatus.PENDING, 1, "+237670000001", FIXED_NOW, null, null, FIXED_NOW.plus(Duration.ofMinutes(10)), null);
	}

	private IntercityAnnouncementRecord announcement(AnnouncementStatus status, int total, int remaining) {
		return new IntercityAnnouncementRecord(
				this.announcementId, this.transporterProfileId, UUID.randomUUID(), null,
				"Douala", "Yaounde", "Bonanjo",
				LocalDate.of(2026, 8, 20), LocalTime.of(8, 30),
				new BigDecimal("9000"), total, remaining, "Bagages",
				status, FIXED_NOW, FIXED_NOW);
	}

	private ProfileRecord profile(UUID profileId, UUID userId, ProfileType type) {
		return new ProfileRecord(profileId, userId, type, ValidationStatus.VALIDATED, "Profil", null, BigDecimal.ZERO,
				true, FIXED_NOW, FIXED_NOW, null, null, null, null, null, null);
	}
}
