package com.motodigo.core.booking.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.booking.config.BookingProperties;
import com.motodigo.core.booking.dto.BookingPageResponse;
import com.motodigo.core.booking.dto.BookingResponse;
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
import com.motodigo.core.profile.repository.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class BookingService {

	public static final ZoneId APP_ZONE = ZoneId.of("Africa/Douala");

	private static final int EXPIRATION_BATCH_SIZE = 100;

	private final BookingRepository bookingRepository;
	private final SeatLockRepository seatLockRepository;
	private final AnnouncementSeatInventory announcementSeatInventory;
	private final ProfileRepository profileRepository;
	private final BookingNotifier bookingNotifier;
	private final BookingProperties bookingProperties;
	private final Clock clock;

	@Autowired
	public BookingService(BookingRepository bookingRepository, SeatLockRepository seatLockRepository,
			AnnouncementSeatInventory announcementSeatInventory, ProfileRepository profileRepository,
			BookingNotifier bookingNotifier, BookingProperties bookingProperties) {
		this(bookingRepository, seatLockRepository, announcementSeatInventory, profileRepository, bookingNotifier,
				bookingProperties, Clock.system(APP_ZONE));
	}

	BookingService(BookingRepository bookingRepository, SeatLockRepository seatLockRepository,
			AnnouncementSeatInventory announcementSeatInventory, ProfileRepository profileRepository,
			BookingNotifier bookingNotifier, BookingProperties bookingProperties, Clock clock) {
		this.bookingRepository = bookingRepository;
		this.seatLockRepository = seatLockRepository;
		this.announcementSeatInventory = announcementSeatInventory;
		this.profileRepository = profileRepository;
		this.bookingNotifier = bookingNotifier;
		this.bookingProperties = bookingProperties;
		this.clock = clock;
	}

	@Transactional
	public BookingResponse requestBooking(UUID userId, UUID activeProfileId, UUID announcementId,
			CreateBookingRequest request) {
		ProfileRecord passenger = requirePassengerProfile(userId, activeProfileId);
		IntercityAnnouncementRecord announcement = this.announcementSeatInventory.findById(announcementId)
				.orElseThrow(() -> new BookingException(BookingErrorCode.BOOKING_NOT_AVAILABLE,
						"Annonce introuvable."));
		assertAnnouncementBookable(announcement, request.seatsRequested());

		if (this.bookingRepository.existsActiveForPassengerAndAnnouncement(announcementId, passenger.id())) {
			throw new BookingException(BookingErrorCode.BOOKING_DUPLICATE);
		}

		Instant now = Instant.now(this.clock);
		Instant expiresAt = now.plus(this.bookingProperties.confirmationTtl());
		BookingRecord created;
		try {
			created = this.bookingRepository.insert(announcementId, passenger.id(), request.seatsRequested(),
					request.contactPhone().trim(), expiresAt);
		}
		catch (DuplicateKeyException ex) {
			throw new BookingException(BookingErrorCode.BOOKING_DUPLICATE);
		}

		this.bookingNotifier.notifyBooking(created, announcement.publisherProfileId(), NotificationType.SUBSCRIPTION);
		return BookingResponse.from(created);
	}

	@Transactional
	public BookingResponse confirmBooking(UUID userId, UUID activeProfileId, UUID bookingId) {
		ProfileRecord publisher = requirePublisherProfile(userId, activeProfileId);
		BookingRecord booking = this.bookingRepository.lockByIdForUpdate(bookingId)
				.orElseThrow(() -> new BookingException(BookingErrorCode.BOOKING_NOT_FOUND));

		IntercityAnnouncementRecord announcement = this.announcementSeatInventory.findById(booking.announcementId())
				.orElseThrow(() -> new BookingException(BookingErrorCode.BOOKING_NOT_FOUND));
		if (!announcement.publisherProfileId().equals(publisher.id())) {
			throw new BookingException(BookingErrorCode.BOOKING_FORBIDDEN);
		}

		if (booking.status() != BookingStatus.PENDING) {
			throw new BookingException(BookingErrorCode.BOOKING_NOT_CONFIRMABLE);
		}
		Instant now = Instant.now(this.clock);
		if (booking.expiresAt() != null && !booking.expiresAt().isAfter(now)) {
			throw new BookingException(BookingErrorCode.BOOKING_NOT_CONFIRMABLE,
					"La demande de souscription a expire.");
		}

		SeatLockRecord lock = lockSeat(announcement.id(), booking, now);
		BookingRecord confirmed = this.bookingRepository.markPendingPayment(booking.id(), now, lock.expiresAt());
		this.bookingNotifier.notifyBooking(confirmed, confirmed.passengerProfileId(), NotificationType.CONFIRMATION);
		return BookingResponse.from(confirmed);
	}

	/**
	 * Transactional seat lock: pessimistic lock on announcement, insert SeatLock, decrement seats.
	 */
	SeatLockRecord lockSeat(UUID announcementId, BookingRecord booking, Instant now) {
		IntercityAnnouncementRecord locked = this.announcementSeatInventory.lockByIdForUpdate(announcementId)
				.orElseThrow(() -> new BookingException(BookingErrorCode.NO_SEAT_AVAILABLE));

		if (locked.status() != AnnouncementStatus.PUBLISHED
				|| locked.remainingSeats() < booking.seatsRequested()) {
			throw new BookingException(BookingErrorCode.NO_SEAT_AVAILABLE);
		}

		Instant lockExpiresAt = now.plus(this.bookingProperties.paymentTtl());
		SeatLockRecord seatLock = this.seatLockRepository.insert(announcementId, booking.id(),
				booking.seatsRequested(), lockExpiresAt);

		try {
			this.announcementSeatInventory.decrementRemainingSeats(announcementId, booking.seatsRequested());
		}
		catch (EmptyResultDataAccessException ex) {
			throw new BookingException(BookingErrorCode.NO_SEAT_AVAILABLE);
		}
		return seatLock;
	}

	@Transactional
	public BookingResponse cancelBooking(UUID userId, UUID activeProfileId, UUID bookingId,
			CancelBookingRequest request) {
		ProfileRecord actor = requireActiveProfile(userId, activeProfileId);
		BookingRecord booking = this.bookingRepository.lockByIdForUpdate(bookingId)
				.orElseThrow(() -> new BookingException(BookingErrorCode.BOOKING_NOT_FOUND));

		IntercityAnnouncementRecord announcement = this.announcementSeatInventory.findById(booking.announcementId())
				.orElseThrow(() -> new BookingException(BookingErrorCode.BOOKING_NOT_FOUND));

		boolean isPassenger = booking.passengerProfileId().equals(actor.id());
		boolean isOwner = announcement.publisherProfileId().equals(actor.id());
		if (!isPassenger && !isOwner) {
			throw new BookingException(BookingErrorCode.BOOKING_FORBIDDEN);
		}

		assertCancellable(booking, announcement, isPassenger, isOwner);

		Instant now = Instant.now(this.clock);
		releaseSeatLockIfPresent(booking.id());
		BookingRecord cancelled = this.bookingRepository.markCancelled(booking.id(),
				request.cancellationReason().trim(), now);
		this.bookingNotifier.notifyBooking(cancelled,
				isPassenger ? announcement.publisherProfileId() : cancelled.passengerProfileId(),
				NotificationType.CANCELLATION);
		return BookingResponse.from(cancelled);
	}

	@Transactional(readOnly = true)
	public BookingResponse getBooking(UUID userId, UUID activeProfileId, UUID bookingId) {
		ProfileRecord actor = requireActiveProfile(userId, activeProfileId);
		BookingRecord booking = this.bookingRepository.findById(bookingId)
				.orElseThrow(() -> new BookingException(BookingErrorCode.BOOKING_NOT_FOUND));
		IntercityAnnouncementRecord announcement = this.announcementSeatInventory.findById(booking.announcementId())
				.orElseThrow(() -> new BookingException(BookingErrorCode.BOOKING_NOT_FOUND));

		boolean allowed = booking.passengerProfileId().equals(actor.id())
				|| announcement.publisherProfileId().equals(actor.id());
		if (!allowed) {
			throw new BookingException(BookingErrorCode.BOOKING_FORBIDDEN);
		}
		return BookingResponse.from(booking);
	}

	@Transactional(readOnly = true)
	public BookingPageResponse listMine(UUID userId, UUID activeProfileId, int page, int size) {
		ProfileRecord passenger = requirePassengerProfile(userId, activeProfileId);
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? 20 : Math.min(size, 100);
		List<BookingRecord> rows = this.bookingRepository.findByPassenger(passenger.id(), safeSize + 1,
				safePage * safeSize);
		return toPage(rows, safePage, safeSize);
	}

	@Transactional(readOnly = true)
	public BookingPageResponse listForAnnouncement(UUID userId, UUID activeProfileId, UUID announcementId, int page,
			int size) {
		ProfileRecord publisher = requirePublisherProfile(userId, activeProfileId);
		IntercityAnnouncementRecord announcement = this.announcementSeatInventory.findById(announcementId)
				.orElseThrow(() -> new BookingException(BookingErrorCode.BOOKING_NOT_FOUND));
		if (!announcement.publisherProfileId().equals(publisher.id())) {
			throw new BookingException(BookingErrorCode.BOOKING_FORBIDDEN);
		}
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? 20 : Math.min(size, 100);
		List<BookingRecord> rows = this.bookingRepository.findByAnnouncement(announcementId, safeSize + 1,
				safePage * safeSize);
		return toPage(rows, safePage, safeSize);
	}

	@Transactional
	public int expireUnpaidBookings() {
		Instant now = Instant.now(this.clock);
		List<BookingRecord> expired = this.bookingRepository.findExpiredPendingOrPendingPayment(now,
				EXPIRATION_BATCH_SIZE);
		int count = 0;
		for (BookingRecord booking : expired) {
			BookingRecord locked = this.bookingRepository.lockByIdForUpdate(booking.id()).orElse(null);
			if (locked == null) {
				continue;
			}
			if (locked.status() != BookingStatus.PENDING && locked.status() != BookingStatus.PENDING_PAYMENT) {
				continue;
			}
			if (locked.expiresAt() == null || locked.expiresAt().isAfter(now)) {
				continue;
			}
			releaseSeatLockIfPresent(locked.id());
			BookingRecord updated = this.bookingRepository.markExpired(locked.id());
			this.bookingNotifier.notifyBooking(updated, updated.passengerProfileId(), NotificationType.EXPIRATION);
			count++;
		}
		return count;
	}

	private void releaseSeatLockIfPresent(UUID bookingId) {
		this.seatLockRepository.lockByBookingIdForUpdate(bookingId).ifPresent(lock -> {
			if (!lock.consumed()) {
				this.announcementSeatInventory.incrementRemainingSeats(lock.announcementId(), lock.seatsLocked());
				this.seatLockRepository.markConsumed(lock.id());
			}
		});
	}

	private void assertAnnouncementBookable(IntercityAnnouncementRecord announcement, int seatsRequested) {
		if (announcement.status() != AnnouncementStatus.PUBLISHED) {
			throw new BookingException(BookingErrorCode.BOOKING_NOT_AVAILABLE);
		}
		if (announcement.remainingSeats() < seatsRequested) {
			throw new BookingException(BookingErrorCode.BOOKING_NOT_AVAILABLE);
		}
		LocalDateTime departure = LocalDateTime.of(announcement.departureDate(), announcement.departureTime());
		LocalDateTime now = LocalDateTime.now(this.clock.withZone(APP_ZONE));
		if (!departure.isAfter(now)) {
			throw new BookingException(BookingErrorCode.BOOKING_NOT_AVAILABLE,
					"Le depart de l'annonce est deja passe.");
		}
	}

	private void assertCancellable(BookingRecord booking, IntercityAnnouncementRecord announcement,
			boolean isPassenger, boolean isOwner) {
		BookingStatus status = booking.status();
		if (status == BookingStatus.CANCELLED || status == BookingStatus.EXPIRED || status == BookingStatus.PAID) {
			throw new BookingException(BookingErrorCode.BOOKING_NOT_CANCELLABLE);
		}

		if (isPassenger) {
			if (status != BookingStatus.PENDING && status != BookingStatus.PENDING_PAYMENT
					&& status != BookingStatus.CONFIRMED) {
				throw new BookingException(BookingErrorCode.BOOKING_NOT_CANCELLABLE);
			}
		}
		else if (isOwner) {
			if (status != BookingStatus.PENDING && status != BookingStatus.PENDING_PAYMENT
					&& status != BookingStatus.CONFIRMED) {
				throw new BookingException(BookingErrorCode.BOOKING_NOT_CANCELLABLE);
			}
		}

		if (status != BookingStatus.PENDING) {
			LocalDateTime departure = LocalDateTime.of(announcement.departureDate(), announcement.departureTime());
			LocalDateTime now = LocalDateTime.now(this.clock.withZone(APP_ZONE));
			if (!departure.isAfter(now)) {
				throw new BookingException(BookingErrorCode.BOOKING_NOT_CANCELLABLE,
						"Annulation refusee: le depart est deja passe.");
			}
		}
	}

	private ProfileRecord requirePassengerProfile(UUID userId, UUID activeProfileId) {
		ProfileRecord profile = requireActiveProfile(userId, activeProfileId);
		if (profile.type() != ProfileType.PASSENGER) {
			throw new BookingException(BookingErrorCode.BOOKING_FORBIDDEN,
					"Un profil passager actif est requis pour souscrire.");
		}
		return profile;
	}

	private ProfileRecord requirePublisherProfile(UUID userId, UUID activeProfileId) {
		ProfileRecord profile = requireActiveProfile(userId, activeProfileId);
		if (profile.type() != ProfileType.TRANSPORTER && profile.type() != ProfileType.AGENCY) {
			throw new BookingException(BookingErrorCode.BOOKING_FORBIDDEN,
					"Un profil transporteur ou agence est requis.");
		}
		return profile;
	}

	private ProfileRecord requireActiveProfile(UUID userId, UUID activeProfileId) {
		if (activeProfileId == null) {
			throw new BookingException(BookingErrorCode.ACTIVE_PROFILE_REQUIRED);
		}
		ProfileRecord profile = this.profileRepository.findOwnedById(userId, activeProfileId)
				.orElseThrow(() -> new BookingException(BookingErrorCode.ACTIVE_PROFILE_REQUIRED));
		if (!profile.active()) {
			throw new BookingException(BookingErrorCode.ACTIVE_PROFILE_REQUIRED);
		}
		return profile;
	}

	private static BookingPageResponse toPage(List<BookingRecord> rows, int page, int size) {
		boolean hasMore = rows.size() > size;
		List<BookingResponse> items = rows.stream().limit(size).map(BookingResponse::from).toList();
		return new BookingPageResponse(items, page, size, hasMore);
	}
}
