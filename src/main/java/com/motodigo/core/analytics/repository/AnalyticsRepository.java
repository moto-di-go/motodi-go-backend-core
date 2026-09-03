package com.motodigo.core.analytics.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AnalyticsRepository {

	private final JdbcTemplate jdbcTemplate;

	public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public int countMonthlyActiveUsers(Instant periodStart, Instant periodEndExclusive) {
		Integer total = this.jdbcTemplate.queryForObject("""
				SELECT count(DISTINCT user_id)
				FROM sessions
				WHERE issued_at >= ? AND issued_at < ?
				""", Integer.class, Timestamp.from(periodStart), Timestamp.from(periodEndExclusive));
		return total == null ? 0 : total;
	}

	public int countActiveVerifiedDrivers(Instant periodStart, Instant periodEndExclusive) {
		Integer total = this.jdbcTemplate.queryForObject("""
				SELECT count(*)
				FROM profiles p
				INNER JOIN driver_last_positions dlp ON dlp.driver_profile_id = p.id
				WHERE p.type = 'MOTOTAXI'
				  AND p.validation_status = 'VALIDATED'
				  AND dlp.captured_at >= ? AND dlp.captured_at < ?
				""", Integer.class, Timestamp.from(periodStart), Timestamp.from(periodEndExclusive));
		return total == null ? 0 : total;
	}

	public int countReservations(Instant periodStart, Instant periodEndExclusive) {
		Integer total = this.jdbcTemplate.queryForObject("""
				SELECT count(*)
				FROM bookings
				WHERE requested_at >= ? AND requested_at < ?
				""", Integer.class, Timestamp.from(periodStart), Timestamp.from(periodEndExclusive));
		return total == null ? 0 : total;
	}

	public Map<String, Long> countReservationsByStatus(Instant periodStart, Instant periodEndExclusive) {
		List<Map<String, Object>> rows = this.jdbcTemplate.queryForList("""
				SELECT status, count(*) AS total
				FROM bookings
				WHERE requested_at >= ? AND requested_at < ?
				GROUP BY status
				ORDER BY status
				""", Timestamp.from(periodStart), Timestamp.from(periodEndExclusive));
		Map<String, Long> result = new LinkedHashMap<>();
		for (Map<String, Object> row : rows) {
			result.put(String.valueOf(row.get("status")), ((Number) row.get("total")).longValue());
		}
		return result;
	}

	public BigDecimal sumConfirmedWalletVolume(Instant periodStart, Instant periodEndExclusive) {
		BigDecimal total = this.jdbcTemplate.queryForObject("""
				SELECT coalesce(sum(amount), 0)
				FROM payment_transactions
				WHERE status = 'CONFIRMED'
				  AND confirmed_at >= ? AND confirmed_at < ?
				""", BigDecimal.class, Timestamp.from(periodStart), Timestamp.from(periodEndExclusive));
		return total == null ? BigDecimal.ZERO : total;
	}

	public BigDecimal sumConfirmedVolumeByProvider(String provider, Instant periodStart, Instant periodEndExclusive) {
		BigDecimal total = this.jdbcTemplate.queryForObject("""
				SELECT coalesce(sum(amount), 0)
				FROM payment_transactions
				WHERE status = 'CONFIRMED'
				  AND provider = ?
				  AND confirmed_at >= ? AND confirmed_at < ?
				""", BigDecimal.class, provider, Timestamp.from(periodStart), Timestamp.from(periodEndExclusive));
		return total == null ? BigDecimal.ZERO : total;
	}

	public SlaSyncMetrics computeSlaAndSync(Instant periodStart, Instant periodEndExclusive) {
		List<SlaSyncMetrics> rows = this.jdbcTemplate.query("""
				SELECT
				  count(*) FILTER (WHERE status = 'SYNCED') AS synced_count,
				  count(*) FILTER (WHERE status = 'FAILED') AS failed_count,
				  avg(EXTRACT(EPOCH FROM (synced_at - queued_at)))
				    FILTER (WHERE status = 'SYNCED' AND synced_at IS NOT NULL) AS avg_sync_seconds
				FROM offline_operations
				WHERE queued_at >= ? AND queued_at < ?
				""", (rs, rowNum) -> new SlaSyncMetrics(
				rs.getLong("synced_count"),
				rs.getLong("failed_count"),
				rs.getBigDecimal("avg_sync_seconds")),
				Timestamp.from(periodStart), Timestamp.from(periodEndExclusive));
		return rows.isEmpty() ? new SlaSyncMetrics(0, 0, null) : rows.getFirst();
	}

	public BigDecimal computeAiUsageRate(LocalDate periodStart, LocalDate periodEnd) {
		List<BigDecimal> rows = this.jdbcTemplate.query("""
				SELECT CASE
				         WHEN coalesce(sum(call_count), 0) = 0 THEN NULL
				         ELSE sum(accepted_suggestion_count)::numeric / sum(call_count)::numeric
				       END AS usage_rate
				FROM ai_usage_metrics
				WHERE period >= ? AND period <= ?
				""", (rs, rowNum) -> rs.getBigDecimal("usage_rate"),
				Date.valueOf(periodStart), Date.valueOf(periodEnd));
		return rows.isEmpty() ? null : rows.getFirst();
	}

	public UUID upsertKpiSnapshot(
			LocalDate periodStart,
			LocalDate periodEnd,
			int monthlyActiveUsers,
			int activeVerifiedDrivers,
			int reservationCount,
			BigDecimal walletVolumeFcfa,
			BigDecimal slaAvailability,
			BigDecimal avgSigSyncSeconds,
			BigDecimal aiUsageRate) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO kpi_snapshots (
				  period_start, period_end, monthly_active_users, active_verified_drivers,
				  reservation_count, wallet_volume_fcfa, sla_availability, avg_sig_sync_seconds, ai_usage_rate
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (period_start, period_end) DO UPDATE SET
				  monthly_active_users = EXCLUDED.monthly_active_users,
				  active_verified_drivers = EXCLUDED.active_verified_drivers,
				  reservation_count = EXCLUDED.reservation_count,
				  wallet_volume_fcfa = EXCLUDED.wallet_volume_fcfa,
				  sla_availability = EXCLUDED.sla_availability,
				  avg_sig_sync_seconds = EXCLUDED.avg_sig_sync_seconds,
				  ai_usage_rate = EXCLUDED.ai_usage_rate,
				  created_at = now()
				RETURNING id
				""", UUID.class,
				Date.valueOf(periodStart),
				Date.valueOf(periodEnd),
				monthlyActiveUsers,
				activeVerifiedDrivers,
				reservationCount,
				walletVolumeFcfa,
				slaAvailability,
				avgSigSyncSeconds,
				aiUsageRate);
	}

	public Instant findSnapshotCreatedAt(UUID id) {
		return this.jdbcTemplate.query("""
				SELECT created_at FROM kpi_snapshots WHERE id = ?
				""", rs -> rs.next() ? rs.getTimestamp("created_at").toInstant() : Instant.now(), id);
	}

	public record SlaSyncMetrics(long syncedCount, long failedCount, BigDecimal avgSigSyncSeconds) {
	}
}
