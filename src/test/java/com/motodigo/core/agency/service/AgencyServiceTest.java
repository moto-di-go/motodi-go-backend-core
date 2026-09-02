package com.motodigo.core.agency.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.agency.dto.AgencyAnnouncementSummaryResponse;
import com.motodigo.core.agency.dto.AgencyBookingPageResponse;
import com.motodigo.core.agency.dto.AgencyBookingSummaryResponse;
import com.motodigo.core.agency.dto.AgencyOverviewResponse;
import com.motodigo.core.agency.exception.AgencyErrorCode;
import com.motodigo.core.agency.exception.AgencyException;
import com.motodigo.core.agency.repository.AgencyRepository;
import com.motodigo.core.agency.repository.AgencyRepository.AgencyProfileBilling;
import com.motodigo.core.agency.service.AgencyService.AgencyAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyServiceTest {

	@Mock
	private AgencyRepository agencyRepository;

	private AgencyService agencyService;

	@BeforeEach
	void setUp() {
		this.agencyService = new AgencyService(this.agencyRepository);
	}

	@Test
	void rejectsMissingAgencyStatsPermission() {
		AgencyAccess access = new AgencyAccess(UUID.randomUUID(), "AGENCY", List.of("PROFILE_READ"), "corr");
		assertThatThrownBy(() -> this.agencyService.overview(access, null, null))
				.isInstanceOf(AgencyException.class)
				.extracting(ex -> ((AgencyException) ex).errorCode())
				.isEqualTo(AgencyErrorCode.AGENCY_ACCESS_DENIED);
		verify(this.agencyRepository, never()).countActiveAnnouncements(any());
	}

	@Test
	void rejectsWrongProfileTypeEvenWithPermission() {
		AgencyAccess access = new AgencyAccess(UUID.randomUUID(), "ADMIN", List.of("AGENCY_STATS_READ"), "corr");
		assertThatThrownBy(() -> this.agencyService.listAnnouncements(access, null, 0, 20))
				.isInstanceOf(AgencyException.class)
				.extracting(ex -> ((AgencyException) ex).errorCode())
				.isEqualTo(AgencyErrorCode.AGENCY_ACCESS_DENIED);
		verify(this.agencyRepository, never()).findAnnouncements(any(), any(), anyInt(), anyInt());
	}

	@Test
	void scopesOverviewQueriesToActiveAgencyProfileId() {
		UUID profileId = UUID.randomUUID();
		AgencyAccess access = agencyAccess(profileId);
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		when(this.agencyRepository.findBilling(profileId))
				.thenReturn(Optional.of(new AgencyProfileBilling("Agence Littoral", "PREMIUM", 0)));
		when(this.agencyRepository.countActiveAnnouncements(profileId)).thenReturn(5L);
		when(this.agencyRepository.countReservations(eq(profileId), any(Instant.class), any(Instant.class)))
				.thenReturn(12L);
		when(this.agencyRepository.countReservationsByStatus(eq(profileId), any(Instant.class), any(Instant.class)))
				.thenReturn(Map.of("CONFIRMED", 8L, "PAID", 4L));
		when(this.agencyRepository.averageFillRate(profileId)).thenReturn(new BigDecimal("0.75"));

		AgencyOverviewResponse response = this.agencyService.overview(access, from, to);

		assertThat(response.activeAnnouncements()).isEqualTo(5L);
		assertThat(response.reservationCount()).isEqualTo(12L);
		assertThat(response.averageFillRate()).isEqualByComparingTo("0.7500");
		assertThat(response.billingPlan()).isEqualTo("PREMIUM");
		assertThat(response.billingPlanPriceFcfa()).isEqualTo(10_000);
		assertThat(response.displayPriority()).isEqualTo(1);
		assertThat(response.legalName()).isEqualTo("Agence Littoral");
		assertThat(response.reservationsByStatus()).containsEntry("CONFIRMED", 8L);
		assertThat(response.periodStart()).isEqualTo(from);
		assertThat(response.periodEnd()).isEqualTo(to);

		verify(this.agencyRepository).countActiveAnnouncements(eq(profileId));
		verify(this.agencyRepository).countReservations(eq(profileId), any(Instant.class), any(Instant.class));
		verify(this.agencyRepository).averageFillRate(eq(profileId));
		verify(this.agencyRepository).findBilling(eq(profileId));
	}

	@Test
	void masksPassengerContactPhoneOnBookings() {
		UUID profileId = UUID.randomUUID();
		AgencyAccess access = agencyAccess(profileId);
		UUID bookingId = UUID.randomUUID();
		when(this.agencyRepository.findBookings(eq(profileId), eq(null), eq(21), eq(0)))
				.thenReturn(List.of(new AgencyBookingSummaryResponse(
						bookingId,
						UUID.randomUUID(),
						UUID.randomUUID(),
						"PAID",
						2,
						"+237690112233",
						Instant.parse("2026-09-01T10:00:00Z"),
						Instant.parse("2026-09-01T11:00:00Z"),
						null,
						null)));

		AgencyBookingPageResponse page = this.agencyService.listBookings(access, null, 0, 20);

		assertThat(page.bookings()).hasSize(1);
		assertThat(page.bookings().getFirst().contactPhone()).isEqualTo("+237****2233");
		verify(this.agencyRepository).findBookings(eq(profileId), eq(null), eq(21), eq(0));
	}

	@Test
	void scopesAnnouncementListToActiveProfileId() {
		UUID profileId = UUID.randomUUID();
		when(this.agencyRepository.findAnnouncements(eq(profileId), eq("PUBLISHED"), eq(21), eq(0)))
				.thenReturn(List.of(new AgencyAnnouncementSummaryResponse(
						UUID.randomUUID(),
						profileId,
						"Douala",
						"Yaounde",
						LocalDate.of(2026, 9, 10),
						null,
						new BigDecimal("9000"),
						14,
						4,
						"PUBLISHED",
						Instant.parse("2026-09-01T08:00:00Z"))));

		var page = this.agencyService.listAnnouncements(agencyAccess(profileId), "PUBLISHED", 0, 20);

		assertThat(page.announcements()).hasSize(1);
		assertThat(page.hasMore()).isFalse();
		verify(this.agencyRepository).findAnnouncements(eq(profileId), eq("PUBLISHED"), eq(21), eq(0));
	}

	private static AgencyAccess agencyAccess(UUID profileId) {
		return new AgencyAccess(profileId, "AGENCY", List.of("AGENCY_STATS_READ"), "corr-1");
	}
}
