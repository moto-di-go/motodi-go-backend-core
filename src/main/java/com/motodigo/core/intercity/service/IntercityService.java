package com.motodigo.core.intercity.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.motodigo.core.intercity.dto.AnnouncementSearchCriteria;
import com.motodigo.core.intercity.dto.CreateIntercityAnnouncementRequest;
import com.motodigo.core.intercity.dto.IntercityAnnouncementMinePageResponse;
import com.motodigo.core.intercity.dto.IntercityAnnouncementResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class IntercityService {

	public static final ZoneId APP_ZONE = ZoneId.of("Africa/Douala");

	private final IntercityAnnouncementRepository announcementRepository;
	private final VehicleLookupRepository vehicleLookupRepository;
	private final ProfileRepository profileRepository;
	private final Clock clock;

	@Autowired
	public IntercityService(IntercityAnnouncementRepository announcementRepository,
			VehicleLookupRepository vehicleLookupRepository, ProfileRepository profileRepository) {
		this(announcementRepository, vehicleLookupRepository, profileRepository, Clock.system(APP_ZONE));
	}

	IntercityService(IntercityAnnouncementRepository announcementRepository,
			VehicleLookupRepository vehicleLookupRepository, ProfileRepository profileRepository, Clock clock) {
		this.announcementRepository = announcementRepository;
		this.vehicleLookupRepository = vehicleLookupRepository;
		this.profileRepository = profileRepository;
		this.clock = clock;
	}

	@Transactional
	public IntercityAnnouncementResponse create(UUID userId, UUID activeProfileId,
			CreateIntercityAnnouncementRequest request) {
		ProfileRecord publisher = requirePublisherProfile(userId, activeProfileId);
		validateCreateFields(request);
		VehicleRecord vehicle = requireValidVehicle(publisher.id(), request.vehicleId(), request.totalSeats());

		IntercityAnnouncementRecord saved = this.announcementRepository.insert(
				publisher.id(),
				vehicle.id(),
				normalizeCity(request.departureCity()),
				normalizeCity(request.destinationCity()),
				normalizeOptional(request.departureQuarter()),
				request.departurePoint(),
				request.destinationPoint(),
				request.departureDate(),
				request.departureTime(),
				request.priceFcfa(),
				request.totalSeats(),
				blankToNull(request.instructions()),
				AnnouncementStatus.PUBLISHED);
		return IntercityAnnouncementResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public IntercityAnnouncementResponse getById(UUID userId, UUID activeProfileId, UUID announcementId) {
		IntercityAnnouncementRecord announcement = this.announcementRepository.findById(announcementId)
				.orElseThrow(() -> new IntercityException(IntercityErrorCode.ANNOUNCEMENT_NOT_FOUND));
		boolean owner = activeProfileId != null && activeProfileId.equals(announcement.publisherProfileId())
				&& this.profileRepository.findOwnedById(userId, activeProfileId).isPresent();
		if (!owner && announcement.status() != AnnouncementStatus.PUBLISHED
				&& announcement.status() != AnnouncementStatus.FULL) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_NOT_FOUND);
		}
		return IntercityAnnouncementResponse.from(announcement);
	}

	@Transactional(readOnly = true)
	public IntercityAnnouncementMinePageResponse listMine(UUID userId, UUID activeProfileId, int page, int size) {
		ProfileRecord publisher = requirePublisherProfile(userId, activeProfileId);
		List<IntercityAnnouncementRecord> rows = this.announcementRepository.findByPublisher(publisher.id(), size + 1,
				page * size);
		boolean hasMore = rows.size() > size;
		List<IntercityAnnouncementResponse> items = rows.stream()
				.limit(size)
				.map(IntercityAnnouncementResponse::from)
				.toList();
		return new IntercityAnnouncementMinePageResponse(items, page, size, hasMore);
	}

	@Transactional
	public IntercityAnnouncementResponse update(UUID userId, UUID activeProfileId, UUID announcementId,
			UpdateIntercityAnnouncementRequest request) {
		ProfileRecord publisher = requirePublisherProfile(userId, activeProfileId);
		IntercityAnnouncementRecord existing = requireOwnedAnnouncement(publisher.id(), announcementId);
		assertModifiable(existing);

		String departureCity = request.departureCity() != null ? normalizeCity(request.departureCity())
				: existing.departureCity();
		String destinationCity = request.destinationCity() != null ? normalizeCity(request.destinationCity())
				: existing.destinationCity();
		if (departureCity.equalsIgnoreCase(destinationCity)) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_INVALID,
					"La ville de depart et la destination doivent etre differentes.");
		}

		LocalDate departureDate = request.departureDate() != null ? request.departureDate() : existing.departureDate();
		LocalTime departureTime = request.departureTime() != null ? request.departureTime() : existing.departureTime();
		assertFutureDeparture(departureDate, departureTime);

		BigDecimal price = request.priceFcfa() != null ? request.priceFcfa() : existing.priceFcfa();
		int totalSeats = request.totalSeats() != null ? request.totalSeats() : existing.totalSeats();
		UUID vehicleId = request.vehicleId() != null ? request.vehicleId() : existing.vehicleId();
		requireValidVehicle(publisher.id(), vehicleId, totalSeats);

		int remainingSeats = existing.remainingSeats();
		if (request.totalSeats() != null) {
			int bookedOrLocked = existing.totalSeats() - existing.remainingSeats();
			if (totalSeats < bookedOrLocked) {
				throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_INVALID,
						"Le nombre de places ne peut pas etre inferieur aux places deja engagees.");
			}
			remainingSeats = totalSeats - bookedOrLocked;
		}

		String quarter = request.departureQuarter() != null ? normalizeOptional(request.departureQuarter())
				: existing.departureQuarter();
		String instructions = request.instructions() != null ? blankToNull(request.instructions())
				: existing.instructions();

		IntercityAnnouncementRecord updated = this.announcementRepository.update(
				existing.id(),
				departureCity,
				destinationCity,
				quarter,
				request.departurePoint(),
				request.destinationPoint(),
				departureDate,
				departureTime,
				price,
				totalSeats,
				remainingSeats,
				vehicleId,
				instructions);
		return IntercityAnnouncementResponse.from(updated);
	}

	@Transactional
	public IntercityAnnouncementResponse close(UUID userId, UUID activeProfileId, UUID announcementId) {
		return transitionStatus(userId, activeProfileId, announcementId, AnnouncementStatus.CLOSED);
	}

	@Transactional
	public IntercityAnnouncementResponse cancel(UUID userId, UUID activeProfileId, UUID announcementId) {
		return transitionStatus(userId, activeProfileId, announcementId, AnnouncementStatus.CANCELLED);
	}

	@Transactional(readOnly = true)
	public IntercityAnnouncementSearchPageResponse search(AnnouncementSearchCriteria criteria) {
		AnnouncementSearchCriteria normalized = normalizeCriteria(criteria);
		List<IntercityAnnouncementRecord> rows = this.announcementRepository.search(normalized);
		boolean hasMore = rows.size() > normalized.size();
		List<IntercityAnnouncementResponse> items = rows.stream()
				.limit(normalized.size())
				.map(IntercityAnnouncementResponse::from)
				.toList();
		List<WideningSuggestion> suggestions = items.isEmpty() ? buildWideningSuggestions(normalized) : List.of();
		return new IntercityAnnouncementSearchPageResponse(items, normalized.page(), normalized.size(), hasMore,
				suggestions);
	}

	List<WideningSuggestion> buildWideningSuggestions(AnnouncementSearchCriteria criteria) {
		List<WideningSuggestion> suggestions = new ArrayList<>();
		if (criteria.date() != null) {
			Map<String, Object> query = baseSuggestedQuery(criteria);
			query.put("dateFrom", criteria.date().minusDays(1).toString());
			query.put("dateTo", criteria.date().plusDays(1).toString());
			suggestions.add(new WideningSuggestion("RELAX_DATE",
					"Essayer la veille ou le lendemain.", query));
		}
		if (criteria.maxPrice() != null) {
			BigDecimal raised = criteria.maxPrice().multiply(new BigDecimal("1.20")).setScale(0, RoundingMode.UP);
			Map<String, Object> query = baseSuggestedQuery(criteria);
			query.put("maxPrice", raised);
			suggestions.add(new WideningSuggestion("RAISE_MAX_PRICE",
					"Augmenter le budget d'environ 20%.", query));
		}
		if (criteria.quarter() != null) {
			Map<String, Object> query = baseSuggestedQuery(criteria);
			query.remove("quarter");
			suggestions.add(new WideningSuggestion("DROP_QUARTER",
					"Retirer le filtre quartier.", query));
		}
		if (criteria.timeFrom() != null || criteria.timeTo() != null) {
			Map<String, Object> query = baseSuggestedQuery(criteria);
			query.remove("timeFrom");
			query.remove("timeTo");
			suggestions.add(new WideningSuggestion("DROP_TIME_RANGE",
					"Elargir la plage horaire.", query));
		}
		if (criteria.onlyAvailable()) {
			Map<String, Object> query = baseSuggestedQuery(criteria);
			query.put("onlyAvailable", false);
			suggestions.add(new WideningSuggestion("INCLUDE_FULL",
					"Inclure les annonces completes.", query));
		}
		if (suggestions.isEmpty()) {
			suggestions.add(new WideningSuggestion("RELAX_DATE",
					"Elargir la date ou le budget de recherche.", Map.of()));
		}
		return List.copyOf(suggestions);
	}

	private IntercityAnnouncementResponse transitionStatus(UUID userId, UUID activeProfileId, UUID announcementId,
			AnnouncementStatus target) {
		ProfileRecord publisher = requirePublisherProfile(userId, activeProfileId);
		IntercityAnnouncementRecord existing = requireOwnedAnnouncement(publisher.id(), announcementId);
		if (existing.status() == AnnouncementStatus.CLOSED || existing.status() == AnnouncementStatus.CANCELLED) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_NOT_MODIFIABLE);
		}
		if (existing.status() == target) {
			return IntercityAnnouncementResponse.from(existing);
		}
		return IntercityAnnouncementResponse.from(this.announcementRepository.updateStatus(existing.id(), target));
	}

	private ProfileRecord requirePublisherProfile(UUID userId, UUID activeProfileId) {
		if (activeProfileId == null) {
			throw new IntercityException(IntercityErrorCode.ACTIVE_PROFILE_REQUIRED);
		}
		ProfileRecord profile = this.profileRepository.findOwnedById(userId, activeProfileId)
				.orElseThrow(() -> new IntercityException(IntercityErrorCode.ACTIVE_PROFILE_REQUIRED));
		if (!profile.active()) {
			throw new IntercityException(IntercityErrorCode.ACTIVE_PROFILE_REQUIRED);
		}
		if (profile.type() != ProfileType.TRANSPORTER && profile.type() != ProfileType.AGENCY) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_FORBIDDEN);
		}
		if (profile.validationStatus() != ValidationStatus.VALIDATED) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_FORBIDDEN,
					"Seuls les profils valides peuvent publier des annonces.");
		}
		return profile;
	}

	private IntercityAnnouncementRecord requireOwnedAnnouncement(UUID publisherProfileId, UUID announcementId) {
		IntercityAnnouncementRecord announcement = this.announcementRepository.findById(announcementId)
				.orElseThrow(() -> new IntercityException(IntercityErrorCode.ANNOUNCEMENT_NOT_FOUND));
		if (!announcement.publisherProfileId().equals(publisherProfileId)) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_FORBIDDEN);
		}
		return announcement;
	}

	private void assertModifiable(IntercityAnnouncementRecord announcement) {
		if (announcement.status() != AnnouncementStatus.DRAFT && announcement.status() != AnnouncementStatus.PUBLISHED) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_NOT_MODIFIABLE);
		}
		if (this.announcementRepository.existsBookingForAnnouncement(announcement.id())) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_NOT_MODIFIABLE,
					"Annonce non modifiable: des reservations existent deja.");
		}
	}

	private VehicleRecord requireValidVehicle(UUID publisherProfileId, UUID vehicleId, int totalSeats) {
		if (vehicleId == null) {
			throw new IntercityException(IntercityErrorCode.VEHICLE_INVALID);
		}
		VehicleRecord vehicle = this.vehicleLookupRepository.findById(vehicleId)
				.orElseThrow(() -> new IntercityException(IntercityErrorCode.VEHICLE_INVALID));
		if (!vehicle.ownerProfileId().equals(publisherProfileId) || !vehicle.active()
				|| !"VALIDATED".equals(vehicle.validationStatus())) {
			throw new IntercityException(IntercityErrorCode.VEHICLE_INVALID);
		}
		if (totalSeats > vehicle.capacity()) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_INVALID,
					"Le nombre de places depasse la capacite du vehicule.");
		}
		return vehicle;
	}

	private void validateCreateFields(CreateIntercityAnnouncementRequest request) {
		if (normalizeCity(request.departureCity()).equalsIgnoreCase(normalizeCity(request.destinationCity()))) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_INVALID,
					"La ville de depart et la destination doivent etre differentes.");
		}
		assertFutureDeparture(request.departureDate(), request.departureTime());
	}

	private void assertFutureDeparture(LocalDate date, LocalTime time) {
		LocalDateTime departure = LocalDateTime.of(date, time);
		LocalDateTime now = LocalDateTime.now(this.clock.withZone(APP_ZONE));
		if (!departure.isAfter(now)) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_INVALID,
					"La date et l'heure de depart doivent etre dans le futur (Africa/Douala).");
		}
	}

	private static AnnouncementSearchCriteria normalizeCriteria(AnnouncementSearchCriteria criteria) {
		int page = Math.max(criteria.page(), 0);
		int size = criteria.size() <= 0 ? 20 : Math.min(criteria.size(), 100);
		AnnouncementSort sort = criteria.sort() == null ? AnnouncementSort.departureTime : criteria.sort();
		return new AnnouncementSearchCriteria(
				normalizeOptional(criteria.departureCity()),
				normalizeOptional(criteria.destinationCity()),
				criteria.date(),
				criteria.maxPrice(),
				criteria.timeFrom(),
				criteria.timeTo(),
				normalizeOptional(criteria.quarter()),
				criteria.onlyAvailable(),
				sort,
				criteria.nearLat(),
				criteria.nearLon(),
				page,
				size);
	}

	private static Map<String, Object> baseSuggestedQuery(AnnouncementSearchCriteria criteria) {
		Map<String, Object> query = new LinkedHashMap<>();
		if (criteria.departureCity() != null) {
			query.put("departureCity", criteria.departureCity());
		}
		if (criteria.destinationCity() != null) {
			query.put("destinationCity", criteria.destinationCity());
		}
		if (criteria.date() != null) {
			query.put("date", criteria.date().toString());
		}
		if (criteria.maxPrice() != null) {
			query.put("maxPrice", criteria.maxPrice());
		}
		if (criteria.timeFrom() != null) {
			query.put("timeFrom", criteria.timeFrom().toString());
		}
		if (criteria.timeTo() != null) {
			query.put("timeTo", criteria.timeTo().toString());
		}
		if (criteria.quarter() != null) {
			query.put("quarter", criteria.quarter());
		}
		query.put("onlyAvailable", criteria.onlyAvailable());
		return query;
	}

	private static String normalizeCity(String value) {
		return value == null ? null : value.trim().replaceAll("\\s+", " ");
	}

	private static String normalizeOptional(String value) {
		if (value == null) {
			return null;
		}
		String normalized = normalizeCity(value);
		return normalized.isBlank() ? null : normalized;
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
