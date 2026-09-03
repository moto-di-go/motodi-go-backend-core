package com.motodigo.core.agency.repository;

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

import com.motodigo.core.agency.dto.AgencyAnnouncementSummaryResponse;
import com.motodigo.core.agency.dto.AgencyBookingSummaryResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AgencyRepository {

	private final JdbcTemplate jdbcTemplate;

	public AgencyRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<AgencyProfileBilling> findBilling(UUID publisherProfileId) {
		List<AgencyProfileBilling> rows = this.jdbcTemplate.query("""
				SELECT legal_name, billing_plan, display_priority
				FROM agency_profiles
				WHERE profile_id = ?
				""", this::mapBilling, publisherProfileId);
		return rows.stream().findFirst();
	}

	public long countActiveAnnouncements(UUID publisherProfileId) {
		Long total = this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM intercity_announcements
				WHERE publisher_profile_id = ?
				  AND status IN ('PUBLISHED', 'FULL')
				""", Long.class, publisherProfileId);
		return total == null ? 0L : total;
	}

	public long countReservations(UUID publisherProfileId, Instant periodStart, Instant periodEndExclusive) {
		Long total = this.jdbcTemplate.queryForObject("""
				SELECT count(*)
				FROM bookings b
				INNER JOIN intercity_announcements a ON a.id = b.announcement_id
				WHERE a.publisher_profile_id = ?
				  AND b.requested_at >= ? AND b.requested_at < ?
				""", Long.class, publisherProfileId, Timestamp.from(periodStart), Timestamp.from(periodEndExclusive));
		return total == null ? 0L : total;
	}

	public Map<String, Long> countReservationsByStatus(UUID publisherProfileId, Instant periodStart,
			Instant periodEndExclusive) {
		Map<String, Long> counts = new LinkedHashMap<>();
		this.jdbcTemplate.query("""
				SELECT b.status, count(*) AS total
				FROM bookings b
				INNER JOIN intercity_announcements a ON a.id = b.announcement_id
				WHERE a.publisher_profile_id = ?
				  AND b.requested_at >= ? AND b.requested_at < ?
				GROUP BY b.status
				ORDER BY b.status
				""", rs -> {
			counts.put(rs.getString("status"), rs.getLong("total"));
		}, publisherProfileId, Timestamp.from(periodStart), Timestamp.from(periodEndExclusive));
		return counts;
	}

	public BigDecimal averageFillRate(UUID publisherProfileId) {
		return this.jdbcTemplate.query("""
				SELECT avg(
				         CASE
				           WHEN total_seats > 0
				             THEN (total_seats - remaining_seats)::numeric / total_seats::numeric
				           ELSE NULL
				         END
				       ) AS fill_rate
				FROM intercity_announcements
				WHERE publisher_profile_id = ?
				""", rs -> {
			if (!rs.next()) {
				return null;
			}
			return rs.getBigDecimal("fill_rate");
		}, publisherProfileId);
	}

	public List<AgencyAnnouncementSummaryResponse> findAnnouncements(UUID publisherProfileId, String status,
			int limit, int offset) {
		StringBuilder sql = new StringBuilder("""
				SELECT id, publisher_profile_id, departure_city, destination_city,
				       departure_date, departure_time, price_fcfa, total_seats, remaining_seats,
				       status, created_at
				FROM intercity_announcements
				WHERE publisher_profile_id = ?
				""");
		List<Object> args = new ArrayList<>();
		args.add(publisherProfileId);
		if (status != null && !status.isBlank()) {
			sql.append(" AND status = ?");
			args.add(status);
		}
		sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);
		return this.jdbcTemplate.query(sql.toString(), this::mapAnnouncement, args.toArray());
	}

	public List<AgencyBookingSummaryResponse> findBookings(UUID publisherProfileId, String status, int limit,
			int offset) {
		StringBuilder sql = new StringBuilder("""
				SELECT b.id, b.announcement_id, b.passenger_profile_id, b.status, b.seats_requested,
				       b.contact_phone, b.requested_at, b.confirmed_at, b.cancelled_at, b.expires_at
				FROM bookings b
				INNER JOIN intercity_announcements a ON a.id = b.announcement_id
				WHERE a.publisher_profile_id = ?
				""");
		List<Object> args = new ArrayList<>();
		args.add(publisherProfileId);
		if (status != null && !status.isBlank()) {
			sql.append(" AND b.status = ?");
			args.add(status);
		}
		sql.append(" ORDER BY b.requested_at DESC, b.id DESC LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);
		return this.jdbcTemplate.query(sql.toString(), this::mapBooking, args.toArray());
	}

	private AgencyProfileBilling mapBilling(ResultSet rs, int rowNum) throws SQLException {
		return new AgencyProfileBilling(
				rs.getString("legal_name"),
				rs.getString("billing_plan"),
				rs.getInt("display_priority"));
	}

	private AgencyAnnouncementSummaryResponse mapAnnouncement(ResultSet rs, int rowNum) throws SQLException {
		LocalTime departureTime = rs.getTime("departure_time") == null
				? null
				: rs.getTime("departure_time").toLocalTime();
		LocalDate departureDate = rs.getDate("departure_date") == null
				? null
				: rs.getDate("departure_date").toLocalDate();
		return new AgencyAnnouncementSummaryResponse(
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

	private AgencyBookingSummaryResponse mapBooking(ResultSet rs, int rowNum) throws SQLException {
		return new AgencyBookingSummaryResponse(
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

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	public record AgencyProfileBilling(String legalName, String billingPlan, int displayPriority) {
	}
}
