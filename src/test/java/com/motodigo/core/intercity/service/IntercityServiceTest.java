package com.motodigo.core.intercity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.intercity.dto.AnnouncementSearchCriteria;
import com.motodigo.core.intercity.dto.CreateIntercityAnnouncementRequest;
import com.motodigo.core.intercity.dto.IntercityAnnouncementSearchPageResponse;
import com.motodigo.core.intercity.dto.UpdateIntercityAnnouncementRequest;
import com.motodigo.core.intercity.dto.WideningSuggestion;
import com.motodigo.core.intercity.entity.AnnouncementSort;
import com.motodigo.core.intercity.entity.AnnouncementStatus;
import com.motodigo.core.intercity.entity.IntercityAnnouncementRecord;
import com.motodigo.core.intercity.entity.VehicleRecord;
import com.motodigo.core.intercity.exception.IntercityErrorCode;
import com.motodigo.core.intercity.exception.IntercityException;
import com.motodigo.core.intercity.repository.IntercityAnnouncementRepository;
import com.motodigo.core.intercity.repository.VehicleLookupRepository;
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
class IntercityServiceTest {

	private static final ZoneId ZONE = IntercityService.APP_ZONE;
	private static final Instant FIXED_NOW = Instant.parse("2026-08-08T12:00:00Z");

	@Mock
	private IntercityAnnouncementRepository announcementRepository;
	@Mock
	private VehicleLookupRepository vehicleLookupRepository;
	@Mock
	private ProfileRepository profileRepository;

	private IntercityService service;
	private UUID userId;
	private UUID profileId;
	private UUID vehicleId;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(FIXED_NOW, ZONE);
		this.service = new IntercityService(this.announcementRepository, this.vehicleLookupRepository,
				this.profileRepository, clock);
		this.userId = UUID.randomUUID();
		this.profileId = UUID.randomUUID();
		this.vehicleId = UUID.randomUUID();
	}

	@Test
	void createRejectsPastDeparture() {
		stubValidatedTransporter();

		CreateIntercityAnnouncementRequest request = new CreateIntercityAnnouncementRequest(
				"Douala", "Yaounde", "Bonanjo", null, null,
				LocalDate.of(2026, 8, 8), LocalTime.of(10, 0),
				new BigDecimal("9000"), 4, this.vehicleId, null);

		assertThatThrownBy(() -> this.service.create(this.userId, this.profileId, request))
				.isInstanceOf(IntercityException.class)
				.extracting(ex -> ((IntercityException) ex).errorCode())
				.isEqualTo(IntercityErrorCode.ANNOUNCEMENT_INVALID);
	}

	@Test
	void createRejectsPendingTransporter() {
		when(this.profileRepository.findOwnedById(this.userId, this.profileId))
				.thenReturn(Optional.of(profile(ProfileType.TRANSPORTER, ValidationStatus.PENDING, true)));

		CreateIntercityAnnouncementRequest request = futureRequest(4);

		assertThatThrownBy(() -> this.service.create(this.userId, this.profileId, request))
				.isInstanceOf(IntercityException.class)
				.extracting(ex -> ((IntercityException) ex).errorCode())
				.isEqualTo(IntercityErrorCode.ANNOUNCEMENT_FORBIDDEN);
	}

	@Test
	void createPersistsPublishedWithRemainingEqualsTotal() {
		stubValidatedTransporter();
		when(this.vehicleLookupRepository.findById(this.vehicleId)).thenReturn(Optional.of(validatedVehicle(14)));
		IntercityAnnouncementRecord saved = announcement(AnnouncementStatus.PUBLISHED, 14, 14);
		when(this.announcementRepository.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
				anyInt(), any(), eq(AnnouncementStatus.PUBLISHED))).thenReturn(saved);
		when(this.profileRepository.findById(this.profileId))
				.thenReturn(Optional.of(profile(ProfileType.TRANSPORTER, ValidationStatus.VALIDATED, true)));
		when(this.announcementRepository.findDepartureCoords(saved.id())).thenReturn(Optional.empty());

		var response = this.service.create(this.userId, this.profileId, futureRequest(14));

		assertThat(response.status()).isEqualTo("PUBLISHED");
		assertThat(response.remainingSeats()).isEqualTo(14);
		assertThat(response.totalSeats()).isEqualTo(14);
	}

	@Test
	void searchReturnsWideningSuggestionsWhenEmpty() {
		when(this.announcementRepository.search(any())).thenReturn(List.of());

		AnnouncementSearchCriteria criteria = new AnnouncementSearchCriteria(
				"Douala", "Yaounde", LocalDate.of(2026, 8, 20), new BigDecimal("5000"),
				LocalTime.of(8, 0), LocalTime.of(12, 0), "Bonanjo", true,
				AnnouncementSort.departureTime, null, null, 0, 20);

		IntercityAnnouncementSearchPageResponse page = this.service.search(criteria);

		assertThat(page.items()).isEmpty();
		assertThat(page.wideningSuggestions()).extracting(WideningSuggestion::code)
				.contains("RELAX_DATE", "RAISE_MAX_PRICE", "DROP_QUARTER", "DROP_TIME_RANGE", "INCLUDE_FULL");
	}

	@Test
	void updateRejectedWhenBookingsExist() {
		stubValidatedTransporter();
		IntercityAnnouncementRecord existing = announcement(AnnouncementStatus.PUBLISHED, 10, 10);
		when(this.announcementRepository.findById(existing.id())).thenReturn(Optional.of(existing));
		when(this.announcementRepository.existsBookingForAnnouncement(existing.id())).thenReturn(true);

		assertThatThrownBy(() -> this.service.update(this.userId, this.profileId, existing.id(),
				new UpdateIntercityAnnouncementRequest(null, null, null, null, null, null, null,
						new BigDecimal("8000"), null, null, null)))
				.isInstanceOf(IntercityException.class)
				.extracting(ex -> ((IntercityException) ex).errorCode())
				.isEqualTo(IntercityErrorCode.ANNOUNCEMENT_NOT_MODIFIABLE);
	}

	@Test
	void closeTransitionsOwnedAnnouncement() {
		stubValidatedTransporter();
		IntercityAnnouncementRecord existing = announcement(AnnouncementStatus.PUBLISHED, 10, 10);
		when(this.announcementRepository.findById(existing.id())).thenReturn(Optional.of(existing));
		when(this.announcementRepository.updateStatus(existing.id(), AnnouncementStatus.CLOSED))
				.thenReturn(announcement(AnnouncementStatus.CLOSED, 10, 10));
		when(this.profileRepository.findById(this.profileId))
				.thenReturn(Optional.of(profile(ProfileType.TRANSPORTER, ValidationStatus.VALIDATED, true)));
		when(this.vehicleLookupRepository.findById(this.vehicleId)).thenReturn(Optional.of(validatedVehicle(10)));
		when(this.announcementRepository.findDepartureCoords(any())).thenReturn(Optional.empty());

		var response = this.service.close(this.userId, this.profileId, existing.id());

		assertThat(response.status()).isEqualTo("CLOSED");
		verify(this.announcementRepository).updateStatus(existing.id(), AnnouncementStatus.CLOSED);
	}

	private void stubValidatedTransporter() {
		when(this.profileRepository.findOwnedById(this.userId, this.profileId))
				.thenReturn(Optional.of(profile(ProfileType.TRANSPORTER, ValidationStatus.VALIDATED, true)));
	}

	private CreateIntercityAnnouncementRequest futureRequest(int seats) {
		return new CreateIntercityAnnouncementRequest(
				"Douala", "Yaounde", "Bonanjo", null, null,
				LocalDate.of(2026, 8, 20), LocalTime.of(8, 30),
				new BigDecimal("9000"), seats, this.vehicleId, "Bagages inclus");
	}

	private VehicleRecord validatedVehicle(int capacity) {
		return new VehicleRecord(this.vehicleId, this.profileId, "BUS", capacity, "VALIDATED", true);
	}

	private IntercityAnnouncementRecord announcement(AnnouncementStatus status, int total, int remaining) {
		return new IntercityAnnouncementRecord(
				UUID.randomUUID(), this.profileId, this.vehicleId, null,
				"Douala", "Yaounde", "Bonanjo",
				LocalDate.of(2026, 8, 20), LocalTime.of(8, 30),
				new BigDecimal("9000"), total, remaining, "Bagages",
				status, FIXED_NOW, FIXED_NOW);
	}

	private ProfileRecord profile(ProfileType type, ValidationStatus status, boolean active) {
		return new ProfileRecord(this.profileId, this.userId, type, status, "Transporteur", null, BigDecimal.ZERO,
				active, FIXED_NOW, FIXED_NOW, null, null, null, null, null, null);
	}
}
