package com.motodigo.core.tracking.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.booking.entity.BookingRecord;
import com.motodigo.core.booking.entity.BookingStatus;
import com.motodigo.core.booking.repository.AnnouncementSeatInventory;
import com.motodigo.core.booking.repository.BookingRepository;
import com.motodigo.core.geo.dto.DriverPositionResponse;
import com.motodigo.core.geo.entity.DriverPositionRecord;
import com.motodigo.core.geo.repository.DriverPositionRepository;
import com.motodigo.core.intercity.entity.IntercityAnnouncementRecord;
import com.motodigo.core.notification.service.NotificationDispatchService;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.repository.ProfileRepository;
import com.motodigo.core.realtime.RealtimeHub;
import com.motodigo.core.tracking.dto.ActiveTripsResponse;
import com.motodigo.core.tracking.dto.TripEventResponse;
import com.motodigo.core.tracking.dto.TripResponse;
import com.motodigo.core.tracking.entity.TripEventRecord;
import com.motodigo.core.tracking.entity.TripRecord;
import com.motodigo.core.tracking.entity.TripStatus;
import com.motodigo.core.tracking.exception.TrackingErrorCode;
import com.motodigo.core.tracking.exception.TrackingException;
import com.motodigo.core.tracking.repository.TripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class TrackingService {

	private final TripRepository tripRepository;
	private final BookingRepository bookingRepository;
	private final AnnouncementSeatInventory announcementSeatInventory;
	private final ProfileRepository profileRepository;
	private final DriverPositionRepository driverPositionRepository;
	private final NotificationDispatchService notificationDispatchService;
	private final RealtimeHub realtimeHub;
	private final Clock clock;

	@Autowired
	public TrackingService(TripRepository tripRepository, BookingRepository bookingRepository,
			AnnouncementSeatInventory announcementSeatInventory, ProfileRepository profileRepository,
			DriverPositionRepository driverPositionRepository,
			NotificationDispatchService notificationDispatchService, RealtimeHub realtimeHub) {
		this(tripRepository, bookingRepository, announcementSeatInventory, profileRepository,
				driverPositionRepository, notificationDispatchService, realtimeHub, Clock.systemUTC());
	}

	TrackingService(TripRepository tripRepository, BookingRepository bookingRepository,
			AnnouncementSeatInventory announcementSeatInventory, ProfileRepository profileRepository,
			DriverPositionRepository driverPositionRepository,
			NotificationDispatchService notificationDispatchService, RealtimeHub realtimeHub, Clock clock) {
		this.tripRepository = tripRepository;
		this.bookingRepository = bookingRepository;
		this.announcementSeatInventory = announcementSeatInventory;
		this.profileRepository = profileRepository;
		this.driverPositionRepository = driverPositionRepository;
		this.notificationDispatchService = notificationDispatchService;
		this.realtimeHub = realtimeHub;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	/**
	 * Creates an INTERCITY trip in {@code CONFIRMED} after a successful ride payment.
	 * Idempotent when the booking already has a linked trip.
	 */
	@Transactional
	public TripRecord createTripFromPaidBooking(UUID bookingId) {
		BookingRecord booking = this.bookingRepository.findById(bookingId)
				.orElseThrow(() -> new TrackingException(TrackingErrorCode.TRIP_NOT_FOUND,
						"Reservation introuvable pour creation de trajet."));
		if (booking.status() != BookingStatus.PAID) {
			throw new TrackingException(TrackingErrorCode.BOOKING_NOT_PAID);
		}
		if (booking.tripId() != null) {
			return this.tripRepository.findById(booking.tripId())
					.orElseThrow(() -> new TrackingException(TrackingErrorCode.TRIP_NOT_FOUND));
		}
		Optional<TripRecord> existing = this.tripRepository.findByBookingId(bookingId);
		if (existing.isPresent()) {
			return existing.get();
		}

		IntercityAnnouncementRecord announcement = this.announcementSeatInventory.findById(booking.announcementId())
				.orElseThrow(() -> new TrackingException(TrackingErrorCode.TRIP_NOT_FOUND,
						"Annonce introuvable pour creation de trajet."));

		UUID transporterProfileId = null;
		ProfileRecord publisher = this.profileRepository.findById(announcement.publisherProfileId()).orElse(null);
		if (publisher != null && publisher.type() == ProfileType.TRANSPORTER) {
			transporterProfileId = publisher.id();
		}

		BigDecimal finalPrice = announcement.priceFcfa()
				.multiply(BigDecimal.valueOf(booking.seatsRequested()));
		Instant plannedDeparture = TripRepository.plannedDeparture(announcement.departureDate(),
				announcement.departureTime());

		TripRecord created = this.tripRepository.insertIntercityTrip(
				booking.passengerProfileId(),
				transporterProfileId,
				announcement.routeId(),
				plannedDeparture,
				finalPrice,
				announcement.departureCity(),
				announcement.destinationCity(),
				announcement.ptRamassage(),
				0);

		this.tripRepository.attachBooking(booking.id(), created.id());
		Instant now = Instant.now(this.clock);
		this.tripRepository.insertEvent(created.id(), TripStatus.CONFIRMED, TripStatus.CONFIRMED.label(),
				"PAYMENT", now);

		TripRecord linked = this.tripRepository.findById(created.id()).orElse(created);
		notifyStatusChange(linked, TripStatus.CONFIRMED, null);
		publishStatusUpdated(linked, TripStatus.CONFIRMED, null);
		return linked;
	}

	@Transactional(readOnly = true)
	public ActiveTripsResponse getActiveTrips(UUID userId, UUID activeProfileId) {
		ProfileRecord profile = requireActiveProfile(userId, activeProfileId);
		List<TripResponse> trips = this.tripRepository.findActiveForProfile(profile.id()).stream()
				.map(this::toResponseWithPosition)
				.toList();
		return new ActiveTripsResponse(trips);
	}

	@Transactional(readOnly = true)
	public TripResponse getTrip(UUID userId, UUID activeProfileId, UUID tripId) {
		ProfileRecord profile = requireActiveProfile(userId, activeProfileId);
		TripRecord trip = this.tripRepository.findById(tripId)
				.orElseThrow(() -> new TrackingException(TrackingErrorCode.TRIP_NOT_FOUND));
		assertCanView(trip, profile.id());
		return toResponseWithPosition(trip);
	}

	@Transactional
	public TripResponse updateStatus(UUID userId, UUID activeProfileId, UUID tripId, String newStatusRaw) {
		ProfileRecord actor = requireActiveProfile(userId, activeProfileId);
		TripStatus newStatus = parseStatus(newStatusRaw);
		TripRecord locked = this.tripRepository.lockByIdForUpdate(tripId)
				.orElseThrow(() -> new TrackingException(TrackingErrorCode.TRIP_NOT_FOUND));
		assertCanMutate(locked, actor.id());

		TripStatus current = locked.status();
		if (!current.canTransitionTo(newStatus)) {
			throw new TrackingException(TrackingErrorCode.INVALID_TRIP_TRANSITION,
					"Transition refusee: " + current.name() + " -> " + newStatus.name());
		}

		Instant now = Instant.now(this.clock);
		Instant startedAt = newStatus == TripStatus.ON_BOARD && locked.startedAt() == null ? now : null;
		Instant completedAt = newStatus == TripStatus.COMPLETED ? now : null;
		String cancellationReason = newStatus == TripStatus.CANCELLED ? "Annule par acteur" : null;

		TripRecord updated = this.tripRepository.updateStatus(tripId, newStatus, startedAt, completedAt,
				cancellationReason);
		TripEventRecord event = this.tripRepository.insertEvent(tripId, newStatus, newStatus.label(),
				"STATUS_UPDATE", now);

		notifyStatusChange(updated, newStatus, actor.id());
		publishStatusUpdated(updated, newStatus, event.id());
		return toResponseWithPosition(updated);
	}

	@Transactional
	public TripResponse completeTrip(UUID userId, UUID activeProfileId, UUID tripId) {
		ProfileRecord actor = requireActiveProfile(userId, activeProfileId);
		TripRecord locked = this.tripRepository.lockByIdForUpdate(tripId)
				.orElseThrow(() -> new TrackingException(TrackingErrorCode.TRIP_NOT_FOUND));
		assertCanMutate(locked, actor.id());

		if (locked.status() == TripStatus.COMPLETED) {
			return toResponseWithPosition(locked);
		}
		if (!locked.status().canTransitionTo(TripStatus.COMPLETED)) {
			throw new TrackingException(TrackingErrorCode.TRIP_NOT_COMPLETABLE);
		}

		Instant now = Instant.now(this.clock);
		Instant startedAt = locked.startedAt() == null ? now : null;
		TripRecord updated = this.tripRepository.updateStatus(tripId, TripStatus.COMPLETED, startedAt, now, null);
		TripEventRecord event = this.tripRepository.insertEvent(tripId, TripStatus.COMPLETED,
				TripStatus.COMPLETED.label(), "COMPLETE", now);

		notifyStatusChange(updated, TripStatus.COMPLETED, actor.id());
		publishStatusUpdated(updated, TripStatus.COMPLETED, event.id());
		return toResponseWithPosition(updated);
	}

	@Transactional(readOnly = true)
	public List<TripEventResponse> getTripEvents(UUID userId, UUID activeProfileId, UUID tripId) {
		ProfileRecord profile = requireActiveProfile(userId, activeProfileId);
		TripRecord trip = this.tripRepository.findById(tripId)
				.orElseThrow(() -> new TrackingException(TrackingErrorCode.TRIP_NOT_FOUND));
		assertCanView(trip, profile.id());
		return this.tripRepository.findEventsByTripId(tripId).stream().map(TripEventResponse::from).toList();
	}

	private TripResponse toResponseWithPosition(TripRecord trip) {
		DriverPositionResponse position = null;
		if (trip.driverProfileId() != null) {
			position = this.driverPositionRepository.findLastByDriver(trip.driverProfileId())
					.map(this::toDriverPositionResponse)
					.orElse(null);
		}
		return TripResponse.from(trip, position);
	}

	private DriverPositionResponse toDriverPositionResponse(DriverPositionRecord position) {
		return new DriverPositionResponse(position.id(), position.driverProfileId(), position.lat(), position.lon(),
				position.speedKmh(), position.accuracyMeters(), position.capturedAt(), position.receivedAt(),
				position.consentConfirmed());
	}

	private void notifyStatusChange(TripRecord trip, TripStatus status, UUID actorProfileId) {
		String title = "Mise a jour trajet";
		String body = status.label();
		String metadata = "{\"tripId\":\"" + trip.id() + "\",\"status\":\"" + status.name() + "\""
				+ (actorProfileId == null ? "" : ",\"actorProfileId\":\"" + actorProfileId + "\"")
				+ "}";

		notifyProfile(trip.passengerProfileId(), trip, title, body, metadata);
		if (trip.transporterProfileId() != null
				&& !Objects.equals(trip.transporterProfileId(), trip.passengerProfileId())) {
			notifyProfile(trip.transporterProfileId(), trip, title, body, metadata);
		}
		if (trip.announcementPublisherProfileId() != null
				&& !Objects.equals(trip.announcementPublisherProfileId(), trip.passengerProfileId())
				&& !Objects.equals(trip.announcementPublisherProfileId(), trip.transporterProfileId())) {
			notifyProfile(trip.announcementPublisherProfileId(), trip, title, body, metadata);
		}
		if (trip.driverProfileId() != null
				&& !Objects.equals(trip.driverProfileId(), trip.passengerProfileId())
				&& !Objects.equals(trip.driverProfileId(), trip.transporterProfileId())
				&& !Objects.equals(trip.driverProfileId(), trip.announcementPublisherProfileId())) {
			notifyProfile(trip.driverProfileId(), trip, title, body, metadata);
		}
	}

	private void notifyProfile(UUID profileId, TripRecord trip, String title, String body, String metadata) {
		if (profileId == null) {
			return;
		}
		this.profileRepository.findById(profileId).ifPresent(profile -> this.notificationDispatchService.enqueue(
				profile.userId(), "STATUS_CHANGE", title, body, trip.bookingId(), null, trip.id(), metadata));
	}

	private void publishStatusUpdated(TripRecord trip, TripStatus status, UUID eventId) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("tripId", trip.id());
		payload.put("status", status.name());
		payload.put("type", trip.type().name());
		payload.put("bookingId", trip.bookingId());
		payload.put("passengerProfileId", trip.passengerProfileId());
		payload.put("driverProfileId", trip.driverProfileId());
		payload.put("transporterProfileId", trip.transporterProfileId());
		payload.put("eventId", eventId);
		payload.put("occurredAt", Instant.now(this.clock).toString());

		this.profileRepository.findById(trip.passengerProfileId()).ifPresent(p -> payload.put("userId", p.userId()));
		this.realtimeHub.publishStub("trip.status.updated", payload);

		// Also fan-out to transporter/publisher user channels when distinct.
		if (trip.transporterProfileId() != null) {
			this.profileRepository.findById(trip.transporterProfileId()).ifPresent(p -> {
				Map<String, Object> copy = new HashMap<>(payload);
				copy.put("userId", p.userId());
				this.realtimeHub.publishStub("trip.status.updated", copy);
			});
		}
		if (trip.announcementPublisherProfileId() != null
				&& !Objects.equals(trip.announcementPublisherProfileId(), trip.transporterProfileId())) {
			this.profileRepository.findById(trip.announcementPublisherProfileId()).ifPresent(p -> {
				Map<String, Object> copy = new HashMap<>(payload);
				copy.put("userId", p.userId());
				this.realtimeHub.publishStub("trip.status.updated", copy);
			});
		}
	}

	private ProfileRecord requireActiveProfile(UUID userId, UUID activeProfileId) {
		if (activeProfileId == null) {
			throw new TrackingException(TrackingErrorCode.ACTIVE_PROFILE_REQUIRED);
		}
		ProfileRecord profile = this.profileRepository.findOwnedById(userId, activeProfileId)
				.orElseThrow(() -> new TrackingException(TrackingErrorCode.ACTIVE_PROFILE_REQUIRED));
		if (!profile.active()) {
			throw new TrackingException(TrackingErrorCode.ACTIVE_PROFILE_REQUIRED);
		}
		return profile;
	}

	private static void assertCanView(TripRecord trip, UUID profileId) {
		if (!isParticipant(trip, profileId)) {
			throw new TrackingException(TrackingErrorCode.TRIP_FORBIDDEN);
		}
	}

	private static void assertCanMutate(TripRecord trip, UUID profileId) {
		if (Objects.equals(trip.driverProfileId(), profileId)
				|| Objects.equals(trip.transporterProfileId(), profileId)
				|| Objects.equals(trip.announcementPublisherProfileId(), profileId)) {
			return;
		}
		throw new TrackingException(TrackingErrorCode.TRIP_FORBIDDEN);
	}

	private static boolean isParticipant(TripRecord trip, UUID profileId) {
		return Objects.equals(trip.passengerProfileId(), profileId)
				|| Objects.equals(trip.driverProfileId(), profileId)
				|| Objects.equals(trip.transporterProfileId(), profileId)
				|| Objects.equals(trip.announcementPublisherProfileId(), profileId);
	}

	private static TripStatus parseStatus(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new TrackingException(TrackingErrorCode.TRIP_VALIDATION_FAILED);
		}
		try {
			return TripStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new TrackingException(TrackingErrorCode.TRIP_VALIDATION_FAILED,
					"Statut de trajet invalide: " + raw);
		}
	}
}
