package com.motodigo.core.ops.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.ops.dto.OpsAnnouncementSummaryResponse;
import com.motodigo.core.ops.dto.OpsBookingSummaryResponse;
import com.motodigo.core.ops.dto.OpsPaymentSummaryResponse;
import com.motodigo.core.ops.dto.OpsProfileListItemResponse;
import com.motodigo.core.ops.dto.OpsProfileSummaryResponse;
import com.motodigo.core.ops.dto.OpsUserDetailResponse;
import com.motodigo.core.ops.dto.OpsUserSummaryResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AdminOpsRepository {

	private final JdbcTemplate jdbcTemplate;

	public AdminOpsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Map<String, Long> countUsersByStatus() {
		return groupCounts("SELECT status, count(*) AS total FROM users GROUP BY status ORDER BY status");
	}

	public long countUsers() {
		Long total = this.jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class);
		return total == null ? 0L : total;
	}

	public long countActiveAnnouncements() {
		Long total = this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM intercity_announcements
				WHERE status IN ('PUBLISHED', 'FULL')
				""", Long.class);
		return total == null ? 0L : total;
	}

	public Map<String, Long> countAnnouncementsByStatus() {
		return groupCounts("""
				SELECT status, count(*) AS total
				FROM intercity_announcements
				GROUP BY status
				ORDER BY status
				""");
	}

	public Map<String, Long> countBookingsByStatus() {
		return groupCounts("""
				SELECT status, count(*) AS total
				FROM bookings
				GROUP BY status
				ORDER BY status
				""");
	}

	public long countPaymentsPendingToday(Instant dayStart, Instant dayEnd) {
		Long total = this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM payment_transactions
				WHERE status IN ('INITIATED', 'PENDING_CALLBACK')
				  AND initiated_at >= ? AND initiated_at < ?
				""", Long.class, Timestamp.from(dayStart), Timestamp.from(dayEnd));
		return total == null ? 0L : total;
	}

	public long countPaymentsConfirmedToday(Instant dayStart, Instant dayEnd) {
		Long total = this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM payment_transactions
				WHERE status = 'CONFIRMED'
				  AND confirmed_at >= ? AND confirmed_at < ?
				""", Long.class, Timestamp.from(dayStart), Timestamp.from(dayEnd));
		return total == null ? 0L : total;
	}

	public Optional<String> findAdminRole(UUID profileId) {
		if (profileId == null) {
			return Optional.empty();
		}
		List<String> rows = this.jdbcTemplate.query("""
				SELECT admin_role FROM admin_profiles WHERE profile_id = ?
				""", (rs, rowNum) -> rs.getString("admin_role"), profileId);
		return rows.stream().findFirst();
	}

	public List<OpsUserSummaryResponse> findUsers(String status, String search, int limit, int offset) {
		StringBuilder sql = new StringBuilder("""
				SELECT u.id, u.phone_number, u.full_name, u.email, u.status, u.created_at,
				       (SELECT count(*) FROM profiles p WHERE p.user_id = u.id) AS profiles_count
				FROM users u
				WHERE 1 = 1
				""");
		List<Object> args = new ArrayList<>();
		if (status != null && !status.isBlank()) {
			sql.append(" AND u.status = ?");
			args.add(status);
		}
		if (search != null && !search.isBlank()) {
			sql.append("""
					 AND (
					   u.phone_number ILIKE ?
					   OR coalesce(u.full_name, '') ILIKE ?
					   OR coalesce(u.email, '') ILIKE ?
					   OR cast(u.id AS text) ILIKE ?
					 )
					""");
			String pattern = "%" + search.trim() + "%";
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
		}
		sql.append(" ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);
		return this.jdbcTemplate.query(sql.toString(), this::mapUserSummary, args.toArray());
	}

	public Optional<OpsUserDetailResponse> findUserDetail(UUID userId) {
		List<OpsUserDetailResponse> users = this.jdbcTemplate.query("""
				SELECT id, phone_number, full_name, email, locale, status,
				       phone_verified_at, created_at, updated_at
				FROM users
				WHERE id = ?
				""", this::mapUserDetailShell, userId);
		if (users.isEmpty()) {
			return Optional.empty();
		}
		OpsUserDetailResponse shell = users.getFirst();
		List<OpsProfileSummaryResponse> profiles = this.jdbcTemplate.query("""
				SELECT id, type, validation_status, display_name, active, created_at
				FROM profiles
				WHERE user_id = ?
				ORDER BY created_at ASC
				""", this::mapProfileSummary, userId);
		return Optional.of(new OpsUserDetailResponse(
				shell.id(),
				shell.phoneNumber(),
				shell.fullName(),
				shell.email(),
				shell.locale(),
				shell.status(),
				shell.phoneVerifiedAt(),
				shell.createdAt(),
				shell.updatedAt(),
				profiles));
	}

	public List<OpsProfileListItemResponse> findProfiles(String validationStatus, String type, String search,
			int limit, int offset) {
		StringBuilder sql = new StringBuilder("""
				SELECT p.id, p.user_id, p.type, p.validation_status, p.display_name, p.active, p.created_at,
				       u.phone_number AS user_phone, u.status AS user_status
				FROM profiles p
				INNER JOIN users u ON u.id = p.user_id
				WHERE 1 = 1
				""");
		List<Object> args = new ArrayList<>();
		if (validationStatus != null && !validationStatus.isBlank()) {
			sql.append(" AND p.validation_status = ?");
			args.add(validationStatus);
		}
		if (type != null && !type.isBlank()) {
			sql.append(" AND p.type = ?");
			args.add(type);
		}
		if (search != null && !search.isBlank()) {
			sql.append("""
					 AND (
					   coalesce(p.display_name, '') ILIKE ?
					   OR u.phone_number ILIKE ?
					   OR cast(p.id AS text) ILIKE ?
					   OR cast(p.user_id AS text) ILIKE ?
					 )
					""");
			String pattern = "%" + search.trim() + "%";
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
		}
		sql.append(" ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);
		return this.jdbcTemplate.query(sql.toString(), this::mapProfileListItem, args.toArray());
	}

	public List<OpsAnnouncementSummaryResponse> findAnnouncements(String status, String search, int limit,
			int offset) {
		StringBuilder sql = new StringBuilder("""
				SELECT id, publisher_profile_id, departure_city, destination_city,
				       departure_date, departure_time, price_fcfa, total_seats, remaining_seats,
				       status, created_at
				FROM intercity_announcements
				WHERE 1 = 1
				""");
		List<Object> args = new ArrayList<>();
		if (status != null && !status.isBlank()) {
			sql.append(" AND status = ?");
			args.add(status);
		}
		if (search != null && !search.isBlank()) {
			sql.append("""
					 AND (
					   departure_city ILIKE ?
					   OR destination_city ILIKE ?
					   OR cast(id AS text) ILIKE ?
					   OR cast(publisher_profile_id AS text) ILIKE ?
					 )
					""");
			String pattern = "%" + search.trim() + "%";
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
		}
		sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);
		return this.jdbcTemplate.query(sql.toString(), this::mapAnnouncement, args.toArray());
	}

	public List<OpsBookingSummaryResponse> findBookings(String status, String search, int limit, int offset) {
		StringBuilder sql = new StringBuilder("""
				SELECT id, announcement_id, passenger_profile_id, status, seats_requested,
				       contact_phone, requested_at, confirmed_at, cancelled_at, expires_at
				FROM bookings
				WHERE 1 = 1
				""");
		List<Object> args = new ArrayList<>();
		if (status != null && !status.isBlank()) {
			sql.append(" AND status = ?");
			args.add(status);
		}
		if (search != null && !search.isBlank()) {
			sql.append("""
					 AND (
					   coalesce(contact_phone, '') ILIKE ?
					   OR cast(id AS text) ILIKE ?
					   OR cast(announcement_id AS text) ILIKE ?
					   OR cast(passenger_profile_id AS text) ILIKE ?
					 )
					""");
			String pattern = "%" + search.trim() + "%";
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
		}
		sql.append(" ORDER BY requested_at DESC, id DESC LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);
		return this.jdbcTemplate.query(sql.toString(), this::mapBooking, args.toArray());
	}

	public List<OpsPaymentSummaryResponse> findPayments(String status, String search, int limit, int offset) {
		StringBuilder sql = new StringBuilder("""
				SELECT id, type, status, amount, currency, provider, payer_phone,
				       external_reference, initiated_at, confirmed_at
				FROM payment_transactions
				WHERE 1 = 1
				""");
		List<Object> args = new ArrayList<>();
		if (status != null && !status.isBlank()) {
			sql.append(" AND status = ?");
			args.add(status);
		}
		if (search != null && !search.isBlank()) {
			sql.append("""
					 AND (
					   coalesce(external_reference, '') ILIKE ?
					   OR coalesce(payer_phone, '') ILIKE ?
					   OR cast(id AS text) ILIKE ?
					 )
					""");
			String pattern = "%" + search.trim() + "%";
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
		}
		sql.append(" ORDER BY initiated_at DESC, id DESC LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);
		return this.jdbcTemplate.query(sql.toString(), this::mapPayment, args.toArray());
	}

	private Map<String, Long> groupCounts(String sql) {
		Map<String, Long> counts = new LinkedHashMap<>();
		this.jdbcTemplate.query(sql, rs -> {
			counts.put(rs.getString("status"), rs.getLong("total"));
		});
		return counts;
	}

	private OpsUserSummaryResponse mapUserSummary(ResultSet rs, int rowNum) throws SQLException {
		return new OpsUserSummaryResponse(
				rs.getObject("id", UUID.class),
				rs.getString("phone_number"),
				rs.getString("full_name"),
				rs.getString("email"),
				rs.getString("status"),
				instant(rs, "created_at"),
				rs.getInt("profiles_count"));
	}

	private OpsUserDetailResponse mapUserDetailShell(ResultSet rs, int rowNum) throws SQLException {
		return new OpsUserDetailResponse(
				rs.getObject("id", UUID.class),
				rs.getString("phone_number"),
				rs.getString("full_name"),
				rs.getString("email"),
				rs.getString("locale"),
				rs.getString("status"),
				instant(rs, "phone_verified_at"),
				instant(rs, "created_at"),
				instant(rs, "updated_at"),
				List.of());
	}

	private OpsProfileSummaryResponse mapProfileSummary(ResultSet rs, int rowNum) throws SQLException {
		return new OpsProfileSummaryResponse(
				rs.getObject("id", UUID.class),
				rs.getString("type"),
				rs.getString("validation_status"),
				rs.getString("display_name"),
				rs.getBoolean("active"),
				instant(rs, "created_at"));
	}

	private OpsProfileListItemResponse mapProfileListItem(ResultSet rs, int rowNum) throws SQLException {
		return new OpsProfileListItemResponse(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("type"),
				rs.getString("validation_status"),
				rs.getString("display_name"),
				rs.getBoolean("active"),
				instant(rs, "created_at"),
				rs.getString("user_phone"),
				rs.getString("user_status"));
	}

	private OpsAnnouncementSummaryResponse mapAnnouncement(ResultSet rs, int rowNum) throws SQLException {
		LocalTime departureTime = rs.getTime("departure_time") == null
				? null
				: rs.getTime("departure_time").toLocalTime();
		LocalDate departureDate = rs.getDate("departure_date") == null
				? null
				: rs.getDate("departure_date").toLocalDate();
		return new OpsAnnouncementSummaryResponse(
				rs.getObject("id", UUID.class),
				rs.getObject("publisher_profile_id", UUID.class),
				rs.getString("departure_city"),
				rs.getString("destination_city"),
				departureDate,
				departureTime,
				rs.getBigDecimal("price_fcfa"),
				rs.getInt("total_seats"),
				rs.getInt("remaining_seats"),
				rs.getString("status"),
				instant(rs, "created_at"));
	}

	private OpsBookingSummaryResponse mapBooking(ResultSet rs, int rowNum) throws SQLException {
		return new OpsBookingSummaryResponse(
				rs.getObject("id", UUID.class),
				rs.getObject("announcement_id", UUID.class),
				rs.getObject("passenger_profile_id", UUID.class),
				rs.getString("status"),
				rs.getInt("seats_requested"),
				rs.getString("contact_phone"),
				instant(rs, "requested_at"),
				instant(rs, "confirmed_at"),
				instant(rs, "cancelled_at"),
				instant(rs, "expires_at"));
	}

	private OpsPaymentSummaryResponse mapPayment(ResultSet rs, int rowNum) throws SQLException {
		BigDecimal amount = rs.getBigDecimal("amount");
		return new OpsPaymentSummaryResponse(
				rs.getObject("id", UUID.class),
				rs.getString("type"),
				rs.getString("status"),
				amount,
				rs.getString("currency"),
				rs.getString("provider"),
				rs.getString("payer_phone"),
				rs.getString("external_reference"),
				instant(rs, "initiated_at"),
				instant(rs, "confirmed_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}
}
