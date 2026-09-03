package com.motodigo.core.agency.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.motodigo.core.agency.dto.AgencyAnnouncementPageResponse;
import com.motodigo.core.agency.dto.AgencyAnnouncementSummaryResponse;
import com.motodigo.core.agency.dto.AgencyBookingPageResponse;
import com.motodigo.core.agency.dto.AgencyBookingSummaryResponse;
import com.motodigo.core.agency.dto.AgencyOverviewResponse;
import com.motodigo.core.agency.exception.AgencyErrorCode;
import com.motodigo.core.agency.exception.AgencyException;
import com.motodigo.core.agency.repository.AgencyRepository;
import com.motodigo.core.agency.repository.AgencyRepository.AgencyProfileBilling;
import com.motodigo.core.booking.entity.BookingStatus;
import com.motodigo.core.intercity.entity.AnnouncementStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AgencyService {

	public static final String AGENCY_STATS_READ_PERMISSION = "AGENCY_STATS_READ";
	public static final String AGENCY_PROFILE_TYPE = "AGENCY";
	public static final ZoneId AGENCY_ZONE = ZoneId.of("Africa/Douala");
	public static final int BILLING_PLAN_PRICE_FCFA = 10_000;

	private final AgencyRepository agencyRepository;

	public AgencyService(AgencyRepository agencyRepository) {
		this.agencyRepository = agencyRepository;
	}

	@Transactional(readOnly = true)
	public AgencyOverviewResponse overview(AgencyAccess access, LocalDate from, LocalDate to) {
		UUID profileId = requireAgencyAccess(access);
		DateRange range = resolvePeriod(from, to);
		Instant start = range.start().atStartOfDay(AGENCY_ZONE).toInstant();
		Instant endExclusive = range.end().plusDays(1).atStartOfDay(AGENCY_ZONE).toInstant();

		AgencyProfileBilling billing = this.agencyRepository.findBilling(profileId)
				.orElse(new AgencyProfileBilling(null, "STANDARD", 0));
		String billingPlan = normalizeBillingPlan(billing.billingPlan());
		int displayPriority = effectiveDisplayPriority(billingPlan, billing.displayPriority());

		BigDecimal fillRate = scaleNullable(this.agencyRepository.averageFillRate(profileId), 4);

		return new AgencyOverviewResponse(
				this.agencyRepository.countActiveAnnouncements(profileId),
				this.agencyRepository.countReservations(profileId, start, endExclusive),
				fillRate,
				billingPlan,
				BILLING_PLAN_PRICE_FCFA,
				displayPriority,
				billing.legalName(),
				Map.copyOf(new LinkedHashMap<>(
						this.agencyRepository.countReservationsByStatus(profileId, start, endExclusive))),
				range.start(),
				range.end());
	}

	@Transactional(readOnly = true)
	public AgencyAnnouncementPageResponse listAnnouncements(AgencyAccess access, String status, int page, int size) {
		UUID profileId = requireAgencyAccess(access);
		String normalizedStatus = normalizeAnnouncementStatus(status);
		List<AgencyAnnouncementSummaryResponse> rows = this.agencyRepository.findAnnouncements(
				profileId, normalizedStatus, size + 1, page * size);
		boolean hasMore = rows.size() > size;
		return new AgencyAnnouncementPageResponse(rows.stream().limit(size).toList(), page, size, hasMore);
	}

	@Transactional(readOnly = true)
	public AgencyBookingPageResponse listBookings(AgencyAccess access, String status, int page, int size) {
		UUID profileId = requireAgencyAccess(access);
		String normalizedStatus = normalizeBookingStatus(status);
		List<AgencyBookingSummaryResponse> rows = this.agencyRepository.findBookings(
				profileId, normalizedStatus, size + 1, page * size);
		boolean hasMore = rows.size() > size;
		List<AgencyBookingSummaryResponse> pageRows = rows.stream()
				.limit(size)
				.map(booking -> new AgencyBookingSummaryResponse(
						booking.id(),
						booking.announcementId(),
						booking.passengerProfileId(),
						booking.status(),
						booking.seatsRequested(),
						maskPhone(booking.contactPhone()),
						booking.requestedAt(),
						booking.confirmedAt(),
						booking.cancelledAt(),
						booking.expiresAt()))
				.toList();
		return new AgencyBookingPageResponse(pageRows, page, size, hasMore);
	}

	static DateRange resolvePeriod(LocalDate from, LocalDate to) {
		LocalDate today = LocalDate.now(AGENCY_ZONE);
		LocalDate start = from;
		LocalDate end = to;
		if (start == null && end == null) {
			start = today.with(TemporalAdjusters.firstDayOfMonth());
			end = today;
		}
		else if (start == null) {
			start = end.with(TemporalAdjusters.firstDayOfMonth());
		}
		else if (end == null) {
			end = today;
		}
		if (end.isBefore(start)) {
			throw new AgencyException(AgencyErrorCode.AGENCY_VALIDATION_FAILED);
		}
		return new DateRange(start, end);
	}

	static int effectiveDisplayPriority(String billingPlan, int displayPriority) {
		if ("PREMIUM".equals(billingPlan) && displayPriority < 1) {
			return 1;
		}
		return displayPriority;
	}

	private static UUID requireAgencyAccess(AgencyAccess access) {
		if (access == null
				|| access.activeProfileId() == null
				|| access.permissions() == null
				|| !access.permissions().contains(AGENCY_STATS_READ_PERMISSION)
				|| !AGENCY_PROFILE_TYPE.equalsIgnoreCase(access.activeProfileType())) {
			throw new AgencyException(AgencyErrorCode.AGENCY_ACCESS_DENIED);
		}
		return access.activeProfileId();
	}

	private static String normalizeBillingPlan(String billingPlan) {
		if (billingPlan == null || billingPlan.isBlank()) {
			return "STANDARD";
		}
		String normalized = billingPlan.trim().toUpperCase();
		return "PREMIUM".equals(normalized) ? "PREMIUM" : "STANDARD";
	}

	private static String normalizeAnnouncementStatus(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return AnnouncementStatus.valueOf(value.trim().toUpperCase()).name();
		}
		catch (IllegalArgumentException ex) {
			throw new AgencyException(AgencyErrorCode.AGENCY_VALIDATION_FAILED);
		}
	}

	private static String normalizeBookingStatus(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return BookingStatus.valueOf(value.trim().toUpperCase()).name();
		}
		catch (IllegalArgumentException ex) {
			throw new AgencyException(AgencyErrorCode.AGENCY_VALIDATION_FAILED);
		}
	}

	static String maskPhone(String phone) {
		if (phone == null || phone.isBlank()) {
			return phone;
		}
		String trimmed = phone.trim();
		if (trimmed.length() < 8) {
			return "****";
		}
		return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
	}

	private static BigDecimal scaleNullable(BigDecimal value, int scale) {
		return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
	}

	public record AgencyAccess(
			UUID activeProfileId,
			String activeProfileType,
			List<String> permissions,
			String correlationId) {

		public AgencyAccess {
			permissions = permissions == null ? List.of() : List.copyOf(permissions);
			activeProfileType = activeProfileType == null || activeProfileType.isBlank()
					? "UNKNOWN"
					: activeProfileType;
		}
	}

	public record DateRange(LocalDate start, LocalDate end) {
	}
}
