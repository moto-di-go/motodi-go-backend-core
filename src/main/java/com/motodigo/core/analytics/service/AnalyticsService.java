package com.motodigo.core.analytics.service;

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

import com.motodigo.core.analytics.dto.BmcTargetsResponse;
import com.motodigo.core.analytics.dto.KpiSnapshotResponse;
import com.motodigo.core.analytics.exception.AnalyticsErrorCode;
import com.motodigo.core.analytics.exception.AnalyticsException;
import com.motodigo.core.analytics.repository.AnalyticsRepository;
import com.motodigo.core.analytics.repository.AnalyticsRepository.SlaSyncMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AnalyticsService {

	public static final String KPI_READ_PERMISSION = "KPI_READ";
	public static final ZoneId KPI_ZONE = ZoneId.of("Africa/Douala");

	public static final BmcTargetsResponse BMC_TARGETS_M6 = new BmcTargetsResponse(
			10_000, 2_000, new BigDecimal("99.9"));
	public static final BmcTargetsResponse BMC_TARGETS_M12 = new BmcTargetsResponse(
			10_000, 2_000, new BigDecimal("99.9"));

	private final AnalyticsRepository analyticsRepository;

	public AnalyticsService(AnalyticsRepository analyticsRepository) {
		this.analyticsRepository = analyticsRepository;
	}

	@Transactional
	public KpiSnapshotResponse computeKpi(AdminAccess access, LocalDate from, LocalDate to, String period) {
		requireKpiRead(access);
		DateRange range = resolvePeriod(from, to, period);
		Instant start = range.start().atStartOfDay(KPI_ZONE).toInstant();
		Instant endExclusive = range.end().plusDays(1).atStartOfDay(KPI_ZONE).toInstant();

		int mau = this.analyticsRepository.countMonthlyActiveUsers(start, endExclusive);
		int drivers = this.analyticsRepository.countActiveVerifiedDrivers(start, endExclusive);
		int reservations = this.analyticsRepository.countReservations(start, endExclusive);
		Map<String, Long> reservationsByStatus = this.analyticsRepository.countReservationsByStatus(start, endExclusive);
		BigDecimal walletVolume = this.analyticsRepository.sumConfirmedWalletVolume(start, endExclusive);
		BigDecimal mtnVolume = this.analyticsRepository.sumConfirmedVolumeByProvider("MTN_MOMO", start, endExclusive);
		BigDecimal orangeVolume = this.analyticsRepository.sumConfirmedVolumeByProvider("ORANGE_MONEY", start, endExclusive);

		SlaSyncMetrics syncMetrics = this.analyticsRepository.computeSlaAndSync(start, endExclusive);
		BigDecimal sla = computeSlaAvailability(syncMetrics.syncedCount(), syncMetrics.failedCount());
		BigDecimal avgSync = scaleNullable(syncMetrics.avgSigSyncSeconds(), 2);
		BigDecimal aiUsageRate = scaleNullable(
				this.analyticsRepository.computeAiUsageRate(range.start(), range.end()), 4);

		UUID snapshotId = this.analyticsRepository.upsertKpiSnapshot(
				range.start(),
				range.end(),
				mau,
				drivers,
				reservations,
				walletVolume,
				sla,
				avgSync,
				aiUsageRate);
		Instant createdAt = this.analyticsRepository.findSnapshotCreatedAt(snapshotId);

		return new KpiSnapshotResponse(
				snapshotId,
				range.start(),
				range.end(),
				mau,
				drivers,
				reservations,
				Map.copyOf(new LinkedHashMap<>(reservationsByStatus)),
				walletVolume,
				mtnVolume,
				orangeVolume,
				sla,
				avgSync,
				aiUsageRate,
				BMC_TARGETS_M6,
				BMC_TARGETS_M12,
				createdAt);
	}

	@Transactional
	public String exportCsv(AdminAccess access, LocalDate from, LocalDate to, String period) {
		KpiSnapshotResponse snapshot = computeKpi(access, from, to, period);
		StringBuilder csv = new StringBuilder();
		csv.append("periodStart,periodEnd,monthlyActiveUsers,activeVerifiedDrivers,reservationCount,")
				.append("walletVolumeFcfa,mtnMomoVolumeFcfa,orangeMoneyVolumeFcfa,slaAvailability,")
				.append("avgSigSyncSeconds,aiUsageRate,bmcMauM6,bmcDriversM6,bmcSlaM6\n");
		csv.append(csvCell(snapshot.periodStart())).append(',')
				.append(csvCell(snapshot.periodEnd())).append(',')
				.append(csvCell(snapshot.monthlyActiveUsers())).append(',')
				.append(csvCell(snapshot.activeVerifiedDrivers())).append(',')
				.append(csvCell(snapshot.reservationCount())).append(',')
				.append(csvCell(snapshot.walletVolumeFcfa())).append(',')
				.append(csvCell(snapshot.mtnMomoVolumeFcfa())).append(',')
				.append(csvCell(snapshot.orangeMoneyVolumeFcfa())).append(',')
				.append(csvCell(snapshot.slaAvailability())).append(',')
				.append(csvCell(snapshot.avgSigSyncSeconds())).append(',')
				.append(csvCell(snapshot.aiUsageRate())).append(',')
				.append(csvCell(BMC_TARGETS_M6.monthlyActiveUsers())).append(',')
				.append(csvCell(BMC_TARGETS_M6.activeVerifiedDrivers())).append(',')
				.append(csvCell(BMC_TARGETS_M6.slaAvailabilityPercent()))
				.append('\n');
		return csv.toString();
	}

	static BigDecimal computeSlaAvailability(long syncedCount, long failedCount) {
		long denominator = syncedCount + failedCount;
		if (denominator == 0) {
			return null;
		}
		return BigDecimal.valueOf(syncedCount)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
	}

	static DateRange resolvePeriod(LocalDate from, LocalDate to, String period) {
		LocalDate today = LocalDate.now(KPI_ZONE);
		if (period != null && !period.isBlank()) {
			String normalized = period.trim().toLowerCase();
			return switch (normalized) {
				case "7d" -> new DateRange(today.minusDays(6), today);
				case "30d" -> new DateRange(today.minusDays(29), today);
				case "month" -> new DateRange(today.with(TemporalAdjusters.firstDayOfMonth()), today);
				default -> throw new AnalyticsException(AnalyticsErrorCode.ANALYTICS_VALIDATION_FAILED);
			};
		}
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
			throw new AnalyticsException(AnalyticsErrorCode.ANALYTICS_VALIDATION_FAILED);
		}
		return new DateRange(start, end);
	}

	private static void requireKpiRead(AdminAccess access) {
		if (access == null || access.permissions() == null
				|| !access.permissions().contains(KPI_READ_PERMISSION)) {
			throw new AnalyticsException(AnalyticsErrorCode.ANALYTICS_ACCESS_DENIED);
		}
	}

	private static BigDecimal scaleNullable(BigDecimal value, int scale) {
		return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
	}

	private static String csvCell(Object value) {
		if (value == null) {
			return "";
		}
		String text = String.valueOf(value);
		if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
			return "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

	public record AdminAccess(
			UUID actorProfileId,
			String actorRole,
			List<String> permissions,
			String correlationId) {

		public AdminAccess {
			permissions = permissions == null ? List.of() : List.copyOf(permissions);
			actorRole = actorRole == null || actorRole.isBlank() ? "UNKNOWN" : actorRole;
		}
	}

	public record DateRange(LocalDate start, LocalDate end) {
	}
}
