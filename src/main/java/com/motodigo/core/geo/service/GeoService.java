package com.motodigo.core.geo.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.geo.config.GeoProperties;
import com.motodigo.core.geo.dto.DriverAvailabilityRequest;
import com.motodigo.core.geo.dto.DriverAvailabilityResponse;
import com.motodigo.core.geo.dto.DriverPositionResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class GeoService {

	private final ProfileRepository profileRepository;
	private final DriverPositionRepository driverPositionRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final GeoProperties properties;
	private final Clock clock;

	@Autowired
	public GeoService(ProfileRepository profileRepository, DriverPositionRepository driverPositionRepository,
			ApplicationEventPublisher eventPublisher, GeoProperties properties) {
		this(profileRepository, driverPositionRepository, eventPublisher, properties, Clock.systemUTC());
	}

	GeoService(ProfileRepository profileRepository, DriverPositionRepository driverPositionRepository,
			ApplicationEventPublisher eventPublisher, GeoProperties properties, Clock clock) {
		this.profileRepository = profileRepository;
		this.driverPositionRepository = driverPositionRepository;
		this.eventPublisher = eventPublisher;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public DriverPositionResponse ingestDriverPosition(UUID userId, UUID activeProfileId, GeoPointRequest request) {
		ProfileRecord driver = requireValidatedMototaxi(userId, activeProfileId);
		if (driver.driver() == null || !driver.driver().gpsConsent()) {
			throw new GeoException(GeoErrorCode.GPS_CONSENT_REQUIRED);
		}
		if (driver.driver().availability() == DriverAvailability.OFFLINE) {
			throw new GeoException(GeoErrorCode.DRIVER_OFFLINE);
		}
		validatePoint(request.lat(), request.lon());
		if (!this.properties.isInsideYaounde(request.lat(), request.lon())) {
			throw new GeoException(GeoErrorCode.OUTSIDE_SERVICE_AREA);
		}
		Instant now = Instant.now(this.clock);
		this.driverPositionRepository.findLastCapturedAt(driver.id()).ifPresent(lastCapturedAt -> {
			if (Duration.between(lastCapturedAt, now).getSeconds() < this.properties.minIngestIntervalSeconds()) {
				throw new GeoException(GeoErrorCode.POSITION_TOO_FREQUENT);
			}
		});
		boolean consentConfirmed = request.consentConfirmed() == null || request.consentConfirmed();
		if (!consentConfirmed) {
			throw new GeoException(GeoErrorCode.GPS_CONSENT_REQUIRED);
		}
		DriverPositionRecord saved = this.driverPositionRepository.insert(driver.id(), request.lat(), request.lon(),
				request.speedKmh(), request.accuracyMeters(), now, true);
		this.driverPositionRepository.upsertLastPosition(saved);
		this.eventPublisher.publishEvent(new DriverPositionUpdatedEvent(saved));
		return toResponse(saved);
	}

	@Transactional
	public DriverAvailabilityResponse updateAvailability(UUID userId, UUID activeProfileId,
			DriverAvailabilityRequest request) {
		ProfileRecord driver = requireValidatedMototaxi(userId, activeProfileId);
		boolean gpsConsent = request.gpsConsent() != null ? request.gpsConsent() : driver.driver().gpsConsent();
		if (request.availability() == DriverAvailability.AVAILABLE && !gpsConsent) {
			throw new GeoException(GeoErrorCode.GPS_CONSENT_REQUIRED);
		}
		this.profileRepository.updateAvailability(driver.id(), request.availability(), gpsConsent);
		ProfileRecord updated = this.profileRepository.findById(driver.id()).orElseThrow();
		return new DriverAvailabilityResponse(updated.id(), updated.driver().availability(), updated.driver().gpsConsent(),
				updated.driver().lastAvailabilityChangeAt());
	}

	@Transactional(readOnly = true)
	public DriverPositionResponse lastPosition(UUID userId, UUID activeProfileId) {
		ProfileRecord driver = requireValidatedMototaxi(userId, activeProfileId);
		return this.driverPositionRepository.findLastByDriver(driver.id())
				.map(this::toResponse)
				.orElseThrow(() -> new GeoException(GeoErrorCode.POSITION_NOT_FOUND));
	}

	@Transactional(readOnly = true)
	public List<DriverPositionRecord> findNearbyAvailable(double lat, double lon, Double radiusKm) {
		validatePoint(lat, lon);
		double radius = radiusKm == null ? this.properties.nearbyDefaultRadiusKm() : radiusKm;
		if (radius <= 0 || radius > this.properties.nearbyMaxRadiusKm()) {
			throw new GeoException(GeoErrorCode.NEARBY_RADIUS_INVALID);
		}
		Instant minCapturedAt = Instant.now(this.clock)
				.minusSeconds(this.properties.lastPositionFreshnessSeconds());
		return this.driverPositionRepository.findNearbyAvailable(lat, lon, radius, minCapturedAt);
	}

	@Transactional
	public int purgeExpiredPositions() {
		Instant cutoff = Instant.now(this.clock).minus(Duration.ofHours(this.properties.historyRetentionHours()));
		return this.driverPositionRepository.deleteOlderThan(cutoff);
	}

	public void validatePoint(Double lat, Double lon) {
		if (lat == null || lon == null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
			throw new GeoException(GeoErrorCode.POSITION_INVALID);
		}
	}

	private ProfileRecord requireValidatedMototaxi(UUID userId, UUID activeProfileId) {
		if (activeProfileId == null) {
			throw new GeoException(GeoErrorCode.ACTIVE_PROFILE_REQUIRED);
		}
		ProfileRecord profile = this.profileRepository.findOwnedById(userId, activeProfileId)
				.orElseThrow(() -> new GeoException(GeoErrorCode.ACTIVE_PROFILE_REQUIRED));
		if (profile.type() != ProfileType.MOTOTAXI || profile.validationStatus() != ValidationStatus.VALIDATED) {
			throw new GeoException(GeoErrorCode.DRIVER_NOT_ALLOWED);
		}
		return profile;
	}

	private DriverPositionResponse toResponse(DriverPositionRecord position) {
		return new DriverPositionResponse(position.id(), position.driverProfileId(), position.lat(), position.lon(),
				position.speedKmh(), position.accuracyMeters(), position.capturedAt(), position.receivedAt(),
				position.consentConfirmed());
	}
}
