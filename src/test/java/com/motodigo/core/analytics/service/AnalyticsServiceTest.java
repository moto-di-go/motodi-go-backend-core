package com.motodigo.core.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.motodigo.core.analytics.dto.KpiSnapshotResponse;
import com.motodigo.core.analytics.exception.AnalyticsErrorCode;
import com.motodigo.core.analytics.exception.AnalyticsException;
import com.motodigo.core.analytics.repository.AnalyticsRepository;
import com.motodigo.core.analytics.repository.AnalyticsRepository.SlaSyncMetrics;
import com.motodigo.core.analytics.service.AnalyticsService.AdminAccess;
import com.motodigo.core.analytics.service.AnalyticsService.DateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

	@Mock
	private AnalyticsRepository analyticsRepository;

	private AnalyticsService analyticsService;

	@BeforeEach
	void setUp() {
		this.analyticsService = new AnalyticsService(this.analyticsRepository);
	}

	@Test
	void rejectsMissingKpiPermission() {
		AdminAccess access = new AdminAccess(UUID.randomUUID(), "ADMIN", List.of("OPERATIONS_READ"), "corr");
		assertThatThrownBy(() -> this.analyticsService.computeKpi(access, null, null, null))
				.isInstanceOf(AnalyticsException.class)
				.extracting(ex -> ((AnalyticsException) ex).errorCode())
				.isEqualTo(AnalyticsErrorCode.ANALYTICS_ACCESS_DENIED);
	}

	@Test
	void rejectsInvalidPeriodBounds() {
		AdminAccess access = kpiAccess();
		assertThatThrownBy(() -> this.analyticsService.computeKpi(
				access, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1), null))
				.isInstanceOf(AnalyticsException.class)
				.extracting(ex -> ((AnalyticsException) ex).errorCode())
				.isEqualTo(AnalyticsErrorCode.ANALYTICS_VALIDATION_FAILED);
	}

	@Test
	void rejectsUnknownPeriodPreset() {
		assertThatThrownBy(() -> AnalyticsService.resolvePeriod(null, null, "year"))
				.isInstanceOf(AnalyticsException.class);
	}

	@Test
	void resolvePeriodSupportsPresetsAndDefaults() {
		DateRange week = AnalyticsService.resolvePeriod(null, null, "7d");
		assertThat(week.end().toEpochDay() - week.start().toEpochDay()).isEqualTo(6);

		DateRange month = AnalyticsService.resolvePeriod(null, null, "month");
		assertThat(month.start().getDayOfMonth()).isEqualTo(1);
	}

	@Test
	void slaIsNullWhenNoSyncedOrFailedOperations() {
		assertThat(AnalyticsService.computeSlaAvailability(0, 0)).isNull();
		assertThat(AnalyticsService.computeSlaAvailability(99, 1))
				.isEqualByComparingTo(new BigDecimal("99.00"));
	}

	@Test
	void computeKpiAggregatesWithoutPiiAndPersistsSnapshot() {
		AdminAccess access = kpiAccess();
		UUID snapshotId = UUID.randomUUID();
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		when(this.analyticsRepository.countMonthlyActiveUsers(any(), any())).thenReturn(120);
		when(this.analyticsRepository.countActiveVerifiedDrivers(any(), any())).thenReturn(45);
		when(this.analyticsRepository.countReservations(any(), any())).thenReturn(80);
		when(this.analyticsRepository.countReservationsByStatus(any(), any()))
				.thenReturn(Map.of("CONFIRMED", 50L, "PAID", 30L));
		when(this.analyticsRepository.sumConfirmedWalletVolume(any(), any()))
				.thenReturn(new BigDecimal("150000.00"));
		when(this.analyticsRepository.sumConfirmedVolumeByProvider(eq("MTN_MOMO"), any(), any()))
				.thenReturn(new BigDecimal("90000.00"));
		when(this.analyticsRepository.sumConfirmedVolumeByProvider(eq("ORANGE_MONEY"), any(), any()))
				.thenReturn(new BigDecimal("60000.00"));
		when(this.analyticsRepository.computeSlaAndSync(any(), any()))
				.thenReturn(new SlaSyncMetrics(99, 1, new BigDecimal("12.345")));
		when(this.analyticsRepository.computeAiUsageRate(from, to)).thenReturn(null);
		when(this.analyticsRepository.upsertKpiSnapshot(
				eq(from), eq(to), eq(120), eq(45), eq(80),
				eq(new BigDecimal("150000.00")),
				eq(new BigDecimal("99.00")),
				eq(new BigDecimal("12.35")),
				isNull())).thenReturn(snapshotId);
		when(this.analyticsRepository.findSnapshotCreatedAt(snapshotId)).thenReturn(Instant.parse("2026-09-02T10:00:00Z"));

		KpiSnapshotResponse response = this.analyticsService.computeKpi(access, from, to, null);

		assertThat(response.id()).isEqualTo(snapshotId);
		assertThat(response.monthlyActiveUsers()).isEqualTo(120);
		assertThat(response.activeVerifiedDrivers()).isEqualTo(45);
		assertThat(response.reservationCount()).isEqualTo(80);
		assertThat(response.reservationsByStatus()).containsEntry("CONFIRMED", 50L);
		assertThat(response.aiUsageRate()).isNull();
		assertThat(response.slaAvailability()).isEqualByComparingTo("99.00");
		assertThat(response.bmcTargetsM6().monthlyActiveUsers()).isEqualTo(10_000);
		assertThat(response.bmcTargetsM6().activeVerifiedDrivers()).isEqualTo(2_000);

		String csv = this.analyticsService.exportCsv(access, from, to, null);
		assertThat(csv).contains("monthlyActiveUsers");
		assertThat(csv).contains("120");
		assertThat(csv).doesNotContain("+237");
		assertThat(csv).doesNotContain("@");

		verify(this.analyticsRepository, org.mockito.Mockito.times(2)).upsertKpiSnapshot(
				eq(from), eq(to), anyInt(), anyInt(), anyInt(), any(), any(), any(), isNull());
	}

	private static AdminAccess kpiAccess() {
		return new AdminAccess(UUID.randomUUID(), "ADMIN", List.of("KPI_READ"), "corr-1");
	}
}
