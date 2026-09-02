package com.motodigo.core.rating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import com.motodigo.core.profile.repository.ProfileRepository;
import com.motodigo.core.rating.dto.RatingRequest;
import com.motodigo.core.rating.entity.RatingRecord;
import com.motodigo.core.rating.entity.RatingStatus;
import com.motodigo.core.rating.exception.RatingErrorCode;
import com.motodigo.core.rating.exception.RatingException;
import com.motodigo.core.rating.repository.RatingRepository;
import com.motodigo.core.tracking.entity.TripRecord;
import com.motodigo.core.tracking.entity.TripStatus;
import com.motodigo.core.tracking.entity.TripType;
import com.motodigo.core.tracking.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

	@Mock
	private RatingRepository ratingRepository;
	@Mock
	private TripRepository tripRepository;
	@Mock
	private ProfileRepository profileRepository;

	private RatingService service;
	private UUID userId;
	private UUID passengerProfileId;
	private UUID driverProfileId;
	private UUID tripId;

	@BeforeEach
	void setUp() {
		this.service = new RatingService(this.ratingRepository, this.tripRepository, this.profileRepository);
		this.userId = UUID.randomUUID();
		this.passengerProfileId = UUID.randomUUID();
		this.driverProfileId = UUID.randomUUID();
		this.tripId = UUID.randomUUID();
	}

	@Test
	void rateTripPublishesAndUpdatesAverage() {
		when(this.profileRepository.findOwnedById(this.userId, this.passengerProfileId))
				.thenReturn(Optional.of(profile(this.passengerProfileId, this.userId)));
		when(this.tripRepository.findById(this.tripId)).thenReturn(Optional.of(completedTrip()));
		when(this.ratingRepository.findByTripRaterRated(this.tripId, this.userId, this.driverProfileId))
				.thenReturn(Optional.empty());
		RatingRecord created = new RatingRecord(UUID.randomUUID(), this.tripId, this.userId, this.driverProfileId, 5,
				"Bon trajet", List.of("PONCTUEL"), RatingStatus.PUBLISHED, Instant.now());
		when(this.ratingRepository.insert(eq(this.tripId), eq(this.userId), eq(this.driverProfileId), eq(5),
				eq("Bon trajet"), eq(List.of("PONCTUEL")), eq(RatingStatus.PUBLISHED))).thenReturn(created);
		when(this.ratingRepository.recalculateAverageForProfile(this.driverProfileId))
				.thenReturn(new BigDecimal("5.00"));

		var response = this.service.rateTrip(this.userId, this.passengerProfileId, this.tripId,
				new RatingRequest(this.driverProfileId, 5, "Bon trajet", List.of("PONCTUEL")));

		assertThat(response.stars()).isEqualTo(5);
		assertThat(response.status()).isEqualTo("PUBLISHED");
		assertThat(response.averageRating()).isEqualByComparingTo("5.00");
		verify(this.ratingRepository).recalculateAverageForProfile(this.driverProfileId);
	}

	@Test
	void insultCommentIsFlagged() {
		when(this.profileRepository.findOwnedById(this.userId, this.passengerProfileId))
				.thenReturn(Optional.of(profile(this.passengerProfileId, this.userId)));
		when(this.tripRepository.findById(this.tripId)).thenReturn(Optional.of(completedTrip()));
		when(this.ratingRepository.findByTripRaterRated(any(), any(), any())).thenReturn(Optional.empty());
		RatingRecord created = new RatingRecord(UUID.randomUUID(), this.tripId, this.userId, this.driverProfileId, 1,
				"Quel connard", List.of(), RatingStatus.FLAGGED, Instant.now());
		when(this.ratingRepository.insert(any(), any(), any(), anyInt(), any(), any(), eq(RatingStatus.FLAGGED)))
				.thenReturn(created);
		when(this.ratingRepository.recalculateAverageForProfile(this.driverProfileId))
				.thenReturn(new BigDecimal("1.00"));

		var response = this.service.rateTrip(this.userId, this.passengerProfileId, this.tripId,
				new RatingRequest(this.driverProfileId, 1, "Quel connard", List.of()));

		assertThat(response.status()).isEqualTo("FLAGGED");
	}

	@Test
	void rateRequiresCompletedTrip() {
		when(this.profileRepository.findOwnedById(this.userId, this.passengerProfileId))
				.thenReturn(Optional.of(profile(this.passengerProfileId, this.userId)));
		TripRecord trip = completedTrip();
		trip = new TripRecord(trip.id(), trip.type(), TripStatus.ON_BOARD, trip.passengerProfileId(),
				trip.driverProfileId(), trip.transporterProfileId(), trip.routeId(), trip.fareEstimateId(),
				trip.originLat(), trip.originLon(), trip.destinationLat(), trip.destinationLon(),
				trip.plannedDepartureAt(), trip.startedAt(), null, trip.finalPriceFcfa(), trip.cancellationReason(),
				trip.createdAt(), trip.bookingId(), trip.announcementPublisherProfileId(), trip.departureCity(),
				trip.destinationCity(), trip.ptRamassage());
		when(this.tripRepository.findById(this.tripId)).thenReturn(Optional.of(trip));

		assertThatThrownBy(() -> this.service.rateTrip(this.userId, this.passengerProfileId, this.tripId,
				new RatingRequest(this.driverProfileId, 5, null, null)))
				.isInstanceOf(RatingException.class)
				.extracting(ex -> ((RatingException) ex).errorCode())
				.isEqualTo(RatingErrorCode.TRIP_NOT_COMPLETED);
	}

	@Test
	void containsInsultDetectsFrenchAndEnglish() {
		assertThat(RatingService.containsInsult("Quel connard")).isTrue();
		assertThat(RatingService.containsInsult("What a bitch")).isTrue();
		assertThat(RatingService.containsInsult("Trajet correct")).isFalse();
	}

	private TripRecord completedTrip() {
		return new TripRecord(this.tripId, TripType.URBAN, TripStatus.COMPLETED, this.passengerProfileId,
				this.driverProfileId, null, null, null, null, null, null, null, Instant.now(), Instant.now(),
				Instant.now(), new BigDecimal("2000"), null, Instant.now(), null, null, null, null, null);
	}

	private static ProfileRecord profile(UUID id, UUID userId) {
		return new ProfileRecord(id, userId, ProfileType.PASSENGER, ValidationStatus.VALIDATED, "Passager", null,
				BigDecimal.ZERO, true, Instant.now(), Instant.now(), null, null, null, null, null, null);
	}
}
