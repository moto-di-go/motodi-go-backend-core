package com.motodigo.core.booking.notify;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motodigo.core.booking.entity.BookingRecord;
import com.motodigo.core.notification.service.NotificationDispatchService;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Persists booking lifecycle notifications, respects preferences, dispatches push, and publishes realtime events.
 */
@Primary
@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationDispatchBookingNotifier implements BookingNotifier {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatchBookingNotifier.class);

	private final NotificationDispatchService dispatchService;
	private final ProfileRepository profileRepository;
	private final ObjectMapper objectMapper;

	public NotificationDispatchBookingNotifier(NotificationDispatchService dispatchService,
			ProfileRepository profileRepository,
			ObjectMapper objectMapper) {
		this.dispatchService = dispatchService;
		this.profileRepository = profileRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	public void notifyBooking(BookingRecord booking, UUID recipientProfileIdHint, NotificationType type) {
		Optional<UUID> userId = resolveUserId(recipientProfileIdHint);
		if (userId.isEmpty()) {
			log.warn("Unable to resolve user for booking notification type={} bookingId={} profileHint={}",
					type, booking.id(), recipientProfileIdHint);
			return;
		}

		String title = titleFor(type);
		String body = bodyFor(type, booking);
		String metadata = metadataJson(booking, recipientProfileIdHint, type);
		this.dispatchService.enqueue(userId.get(), type.name(), title, body, booking.id(), null, booking.tripId(),
				metadata);
	}

	private Optional<UUID> resolveUserId(UUID profileIdHint) {
		if (profileIdHint == null) {
			return Optional.empty();
		}
		return this.profileRepository.findById(profileIdHint).map(ProfileRecord::userId);
	}

	private static String titleFor(NotificationType type) {
		return switch (type) {
			case SUBSCRIPTION -> "Nouvelle reservation";
			case CONFIRMATION -> "Reservation confirmee";
			case CANCELLATION -> "Reservation annulee";
			case EXPIRATION -> "Reservation expiree";
			case PAYMENT -> "Paiement reservation";
		};
	}

	private static String bodyFor(NotificationType type, BookingRecord booking) {
		return switch (type) {
			case SUBSCRIPTION -> "Un passager a demande " + booking.seatsRequested()
					+ " place(s) sur votre annonce.";
			case CONFIRMATION -> "Votre reservation a ete confirmee. Finalisez le paiement si requis.";
			case CANCELLATION -> "Une reservation a ete annulee"
					+ (booking.cancellationReason() == null || booking.cancellationReason().isBlank()
							? "."
							: " : " + booking.cancellationReason());
			case EXPIRATION -> "Votre reservation a expire faute de confirmation ou de paiement a temps.";
			case PAYMENT -> "Mise a jour de paiement pour votre reservation.";
		};
	}

	private String metadataJson(BookingRecord booking, UUID recipientProfileId, NotificationType type) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("bookingId", booking.id());
		metadata.put("announcementId", booking.announcementId());
		metadata.put("recipientProfileId", recipientProfileId);
		metadata.put("status", booking.status() == null ? null : booking.status().name());
		metadata.put("notificationType", type.name());
		try {
			return this.objectMapper.writeValueAsString(metadata);
		}
		catch (JsonProcessingException ex) {
			return "{}";
		}
	}
}
