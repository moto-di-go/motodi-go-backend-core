package com.motodigo.core.geo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.geo.config.GeoProperties;
import com.motodigo.core.geo.dto.DriverAvailabilityRequest;
import com.motodigo.core.geo.dto.GeoPointRequest;
import com.motodigo.core.geo.entity.DriverPositionRecord;
import com.motodigo.core.geo.event.DriverPositionUpdatedEvent;
import com.motodigo.core.geo.exception.GeoErrorCode;
import com.motodigo.core.geo.exception.GeoException;
import com.motodigo.core.geo.repository.DriverPositionRepository;
import com.motodigo.core.profile.entity.DriverAvailability;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import com.motodigo.core.profile.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class GeoServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");

	@Mock
	private ProfileRepository profileRepository;

	@Mock
	private DriverPositionRepository driverPositionRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private GeoService geoService;
	private UUID userId;
	private UUID profileId;

	@BeforeEach
	void setUp() {
		this.userId = UUID.randomUUID();
		this.profileId = UUID.randomUUID();
		GeoProperties properties = new GeoProperties(3, 5, 60, 24, 2, 10, 3.72, 4.05, 11.38, 11.64, "./data/gis/routes_yaounde.gpkg");
		this.geoService = new GeoService(this.profileRepository, this.driverPositionRepository, this.eventPublisher,
				properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void ingestPublishesEventForValidatedAvailableDriver() {
		when(this.profileRepository.findOwnedById(this.userId, this.profileId))
				.thenReturn(Optional.of(mototaxi(ValidationStatus.VALIDATED, DriverAvailability.AVAILABLE, true)));
		when(this.driverPositionRepository.findLastCapturedAt(this.profileId)).thenReturn(Optional.empty());
		DriverPositionRecord saved = new DriverPositionRecord(UUID.randomUUID(), this.profileId, 3.87, 11.52, 20.0, 8.0,
				NOW, NOW, true);
		when(this.driverPositionRepository.insert(eq(this.profileId), eq(3.87), eq(11.52), eq(20.0), eq(8.0), eq(NOW),
				eq(true))).thenReturn(saved);

		var response = this.geoService.ingestDriverPosition(this.userId, this.profileId,
				new GeoPointRequest(3.87, 11.52, 20.0, 8.0, true));

		assertThat(response.lat()).isEqualTo(3.87);
		verify(this.driverPositionRepository).upsertLastPosition(saved);
		ArgumentCaptor<DriverPositionUpdatedEvent> event = ArgumentCaptor.forClass(DriverPositionUpdatedEvent.class);
		verify(this.eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().position().id()).isEqualTo(saved.id());
	}

	@Test
	void ingestRejectsPendingDriver() {
		when(this.profileRepository.findOwnedById(this.userId, this.profileId))
				.thenReturn(Optional.of(mototaxi(ValidationStatus.PENDING, DriverAvailability.AVAILABLE, true)));

		assertThatThrownBy(() -> this.geoService.ingestDriverPosition(this.userId, this.profileId,
				new GeoPointRequest(3.87, 11.52, null, null, true)))
				.isInstanceOf(GeoException.class)
				.extracting(ex -> ((GeoException) ex).errorCode())
				.isEqualTo(GeoErrorCode.DRIVER_NOT_ALLOWED);
		verify(this.driverPositionRepository, never()).insert(any(), anyDouble(), anyDouble(), any(), any(), any(),
				anyBoolean());
	}

	@Test
	void ingestRejectsTooFrequentUpdates() {
		when(this.profileRepository.findOwnedById(this.userId, this.profileId))
				.thenReturn(Optional.of(mototaxi(ValidationStatus.VALIDATED, DriverAvailability.AVAILABLE, true)));
		when(this.driverPositionRepository.findLastCapturedAt(this.profileId))
				.thenReturn(Optional.of(NOW.minusSeconds(2)));

		assertThatThrownBy(() -> this.geoService.ingestDriverPosition(this.userId, this.profileId,
				new GeoPointRequest(3.87, 11.52, null, null, true)))
				.isInstanceOf(GeoException.class)
				.extracting(ex -> ((GeoException) ex).errorCode())
				.isEqualTo(GeoErrorCode.POSITION_TOO_FREQUENT);
	}

	@Test
	void updateAvailabilityRequiresConsentWhenGoingAvailable() {
		when(this.profileRepository.findOwnedById(this.userId, this.profileId))
				.thenReturn(Optional.of(mototaxi(ValidationStatus.VALIDATED, DriverAvailability.OFFLINE, false)));

		assertThatThrownBy(() -> this.geoService.updateAvailability(this.userId, this.profileId,
				new DriverAvailabilityRequest(DriverAvailability.AVAILABLE, false)))
				.isInstanceOf(GeoException.class)
				.extracting(ex -> ((GeoException) ex).errorCode())
				.isEqualTo(GeoErrorCode.GPS_CONSENT_REQUIRED);
	}

	private ProfileRecord mototaxi(ValidationStatus status, DriverAvailability availability, boolean gpsConsent) {
		return new ProfileRecord(this.profileId, this.userId, ProfileType.MOTOTAXI, status, "Bika", null, null, true,
				NOW, NOW, null, new ProfileRecord.DriverDetails(availability, gpsConsent, NOW),
				new ProfileRecord.MotoTaxiDetails("LIC-1", "Yaounde"), null, null, null);
	}
}
