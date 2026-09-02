package com.motodigo.core.rating.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.repository.ProfileRepository;
import com.motodigo.core.rating.dto.RatingRequest;
import com.motodigo.core.rating.dto.RatingResponse;
import com.motodigo.core.rating.entity.RatingRecord;
import com.motodigo.core.rating.entity.RatingStatus;
import com.motodigo.core.rating.exception.RatingErrorCode;
import com.motodigo.core.rating.exception.RatingException;
import com.motodigo.core.rating.repository.RatingRepository;
import com.motodigo.core.tracking.entity.TripRecord;
import com.motodigo.core.tracking.entity.TripStatus;
import com.motodigo.core.tracking.repository.TripRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class RatingService {

	private static final Set<String> ALLOWED_TAGS = Set.of(
			"PONCTUEL", "CONDUITE_SURE", "PRIX_RESPECTE", "VEHICULE_PROPRE", "PROBLEME_ITINERAIRE");

	private static final Pattern INSULT_PATTERN = Pattern.compile(
			"\\b(connard|connasse|salope|putain|merde|encule|enculé|fdp|pd|bite|fuck|shit|bitch|asshole|bastard|cunt|dick)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private final RatingRepository ratingRepository;
	private final TripRepository tripRepository;
	private final ProfileRepository profileRepository;

	public RatingService(RatingRepository ratingRepository, TripRepository tripRepository,
			ProfileRepository profileRepository) {
		this.ratingRepository = ratingRepository;
		this.tripRepository = tripRepository;
		this.profileRepository = profileRepository;
	}

	@Transactional
	public RatingResponse rateTrip(UUID userId, UUID activeProfileId, UUID tripId, RatingRequest request) {
		if (request == null || request.ratedProfileId() == null || request.stars() == null) {
			throw new RatingException(RatingErrorCode.RATING_VALIDATION_FAILED);
		}
		ProfileRecord actor = requireActiveProfile(userId, activeProfileId);
		TripRecord trip = this.tripRepository.findById(tripId)
				.orElseThrow(() -> new RatingException(RatingErrorCode.TRIP_NOT_FOUND));
		assertParticipant(trip, actor.id());
		if (trip.status() != TripStatus.COMPLETED) {
			throw new RatingException(RatingErrorCode.TRIP_NOT_COMPLETED);
		}
		if (Objects.equals(request.ratedProfileId(), actor.id())) {
			throw new RatingException(RatingErrorCode.RATED_PROFILE_INVALID, "Impossible de s'auto-evaluer.");
		}
		if (!isParticipant(trip, request.ratedProfileId())) {
			throw new RatingException(RatingErrorCode.RATED_PROFILE_INVALID);
		}
		if (this.ratingRepository.findByTripRaterRated(tripId, userId, request.ratedProfileId()).isPresent()) {
			throw new RatingException(RatingErrorCode.RATING_ALREADY_EXISTS);
		}

		List<String> tags = normalizeTags(request.tags());
		RatingStatus status = containsInsult(request.comment()) ? RatingStatus.FLAGGED : RatingStatus.PUBLISHED;

		RatingRecord created;
		try {
			created = this.ratingRepository.insert(tripId, userId, request.ratedProfileId(), request.stars(),
					blankToNull(request.comment()), tags, status);
		}
		catch (DuplicateKeyException ex) {
			throw new RatingException(RatingErrorCode.RATING_ALREADY_EXISTS);
		}

		BigDecimal average = this.ratingRepository.recalculateAverageForProfile(request.ratedProfileId());
		return RatingResponse.from(created, average);
	}

	@Transactional(readOnly = true)
	public List<RatingResponse> listForTrip(UUID userId, UUID activeProfileId, UUID tripId) {
		ProfileRecord actor = requireActiveProfile(userId, activeProfileId);
		TripRecord trip = this.tripRepository.findById(tripId)
				.orElseThrow(() -> new RatingException(RatingErrorCode.TRIP_NOT_FOUND));
		assertParticipant(trip, actor.id());
		return this.ratingRepository.findByTripAndRater(tripId, userId).stream()
				.map(rating -> {
					BigDecimal average = this.profileRepository.findById(rating.ratedProfileId())
							.map(ProfileRecord::averageRating)
							.orElse(null);
					return RatingResponse.from(rating, average);
				})
				.toList();
	}

	static boolean containsInsult(String comment) {
		if (comment == null || comment.isBlank()) {
			return false;
		}
		return INSULT_PATTERN.matcher(comment).find();
	}

	private ProfileRecord requireActiveProfile(UUID userId, UUID activeProfileId) {
		if (activeProfileId == null) {
			throw new RatingException(RatingErrorCode.RATING_FORBIDDEN);
		}
		ProfileRecord profile = this.profileRepository.findOwnedById(userId, activeProfileId)
				.orElseThrow(() -> new RatingException(RatingErrorCode.RATING_FORBIDDEN));
		if (!profile.active()) {
			throw new RatingException(RatingErrorCode.RATING_FORBIDDEN);
		}
		return profile;
	}

	private static void assertParticipant(TripRecord trip, UUID profileId) {
		if (!isParticipant(trip, profileId)) {
			throw new RatingException(RatingErrorCode.RATING_FORBIDDEN);
		}
	}

	private static boolean isParticipant(TripRecord trip, UUID profileId) {
		return Objects.equals(trip.passengerProfileId(), profileId)
				|| Objects.equals(trip.driverProfileId(), profileId)
				|| Objects.equals(trip.transporterProfileId(), profileId)
				|| Objects.equals(trip.announcementPublisherProfileId(), profileId);
	}

	private static List<String> normalizeTags(List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return List.of();
		}
		return tags.stream()
				.filter(Objects::nonNull)
				.map(tag -> tag.trim().toUpperCase(Locale.ROOT))
				.filter(ALLOWED_TAGS::contains)
				.distinct()
				.toList();
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
