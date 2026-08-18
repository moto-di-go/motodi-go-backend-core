package com.motodigo.core.booking.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.booking.entity.BookingRecord;
import com.motodigo.core.booking.entity.BookingStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class BookingRepository {

	private static final String SELECT_BASE = """
			SELECT id, announcement_id, passenger_profile_id, trip_id, status, seats_requested, contact_phone,
			       requested_at, confirmed_at, cancelled_at, expires_at, cancellation_reason
			FROM bookings
			""";

	private final JdbcTemplate jdbcTemplate;

	public BookingRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public BookingRecord insert(UUID announcementId, UUID passengerProfileId, int seatsRequested, String contactPhone,
			Instant expiresAt) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO bookings (announcement_id, passenger_profile_id, status, seats_requested, contact_phone, expires_at)
				VALUES (?, ?, 'PENDING', ?, ?, ?)
				RETURNING id, announcement_id, passenger_profile_id, trip_id, status, seats_requested, contact_phone,
				          requested_at, confirmed_at, cancelled_at, expires_at, cancellation_reason
				""", this::mapBooking, announcementId, passengerProfileId, seatsRequested, contactPhone,
				Timestamp.from(expiresAt));
	}

	public Optional<BookingRecord> findById(UUID id) {
		return this.jdbcTemplate.query(SELECT_BASE + " WHERE id = ?", this::mapBooking, id).stream().findFirst();
	}

	public Optional<BookingRecord> lockByIdForUpdate(UUID id) {
		return this.jdbcTemplate.query(SELECT_BASE + " WHERE id = ? FOR UPDATE", this::mapBooking, id).stream()
				.findFirst();
	}

	public List<BookingRecord> findByPassenger(UUID passengerProfileId, int limit, int offset) {
		return this.jdbcTemplate.query(SELECT_BASE + """
				WHERE passenger_profile_id = ?
				ORDER BY requested_at DESC
				LIMIT ? OFFSET ?
				""", this::mapBooking, passengerProfileId, limit, offset);
	}

	public List<BookingRecord> findByAnnouncement(UUID announcementId, int limit, int offset) {
		return this.jdbcTemplate.query(SELECT_BASE + """
				WHERE announcement_id = ?
				ORDER BY requested_at DESC
				LIMIT ? OFFSET ?
				""", this::mapBooking, announcementId, limit, offset);
	}

	public boolean existsActiveForPassengerAndAnnouncement(UUID announcementId, UUID passengerProfileId) {
		Boolean exists = this.jdbcTemplate.queryForObject("""
				SELECT EXISTS (
				  SELECT 1 FROM bookings
				  WHERE announcement_id = ?
				    AND passenger_profile_id = ?
				    AND status IN ('PENDING', 'CONFIRMED', 'PAID', 'PENDING_PAYMENT')
				)
				""", Boolean.class, announcementId, passengerProfileId);
		return Boolean.TRUE.equals(exists);
	}

	public BookingRecord updateStatus(UUID id, BookingStatus status, Instant confirmedAt, Instant cancelledAt,
			Instant expiresAt, String cancellationReason) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE bookings
				SET status = ?,
				    confirmed_at = COALESCE(?, confirmed_at),
				    cancelled_at = COALESCE(?, cancelled_at),
				    expires_at = COALESCE(?, expires_at),
				    cancellation_reason = COALESCE(?, cancellation_reason)
				WHERE id = ?
				RETURNING id, announcement_id, passenger_profile_id, trip_id, status, seats_requested, contact_phone,
				          requested_at, confirmed_at, cancelled_at, expires_at, cancellation_reason
				""", this::mapBooking,
				status.name(),
				confirmedAt == null ? null : Timestamp.from(confirmedAt),
				cancelledAt == null ? null : Timestamp.from(cancelledAt),
				expiresAt == null ? null : Timestamp.from(expiresAt),
				cancellationReason,
				id);
	}

	public BookingRecord markCancelled(UUID id, String reason, Instant cancelledAt) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE bookings
				SET status = 'CANCELLED',
				    cancelled_at = ?,
				    cancellation_reason = ?
				WHERE id = ?
				RETURNING id, announcement_id, passenger_profile_id, trip_id, status, seats_requested, contact_phone,
				          requested_at, confirmed_at, cancelled_at, expires_at, cancellation_reason
				""", this::mapBooking, Timestamp.from(cancelledAt), reason, id);
	}

	public BookingRecord markExpired(UUID id) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE bookings
				SET status = 'EXPIRED'
				WHERE id = ?
				RETURNING id, announcement_id, passenger_profile_id, trip_id, status, seats_requested, contact_phone,
				          requested_at, confirmed_at, cancelled_at, expires_at, cancellation_reason
				""", this::mapBooking, id);
	}

	public BookingRecord markPendingPayment(UUID id, Instant confirmedAt, Instant expiresAt) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE bookings
				SET status = 'PENDING_PAYMENT',
				    confirmed_at = ?,
				    expires_at = ?
				WHERE id = ?
				RETURNING id, announcement_id, passenger_profile_id, trip_id, status, seats_requested, contact_phone,
				          requested_at, confirmed_at, cancelled_at, expires_at, cancellation_reason
				""", this::mapBooking, Timestamp.from(confirmedAt), Timestamp.from(expiresAt), id);
	}

	public List<BookingRecord> findExpiredPendingOrPendingPayment(Instant now, int limit) {
		return this.jdbcTemplate.query(SELECT_BASE + """
				WHERE status IN ('PENDING', 'PENDING_PAYMENT')
				  AND expires_at IS NOT NULL
				  AND expires_at < ?
				ORDER BY expires_at ASC
				LIMIT ?
				""", this::mapBooking, Timestamp.from(now), limit);
	}

	private BookingRecord mapBooking(ResultSet rs, int rowNum) throws SQLException {
		return new BookingRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("announcement_id", UUID.class),
				rs.getObject("passenger_profile_id", UUID.class),
				rs.getObject("trip_id", UUID.class),
				BookingStatus.valueOf(rs.getString("status")),
				rs.getInt("seats_requested"),
				rs.getString("contact_phone"),
				toInstant(rs.getObject("requested_at")),
				toInstantNullable(rs.getObject("confirmed_at")),
				toInstantNullable(rs.getObject("cancelled_at")),
				toInstantNullable(rs.getObject("expires_at")),
				rs.getString("cancellation_reason"));
	}

	private static Instant toInstant(Object value) {
		Instant instant = toInstantNullable(value);
		if (instant == null) {
			throw new IllegalArgumentException("Timestamp required but was null");
		}
		return instant;
	}

	private static Instant toInstantNullable(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof OffsetDateTime odt) {
			return odt.toInstant();
		}
		if (value instanceof Timestamp ts) {
			return ts.toInstant();
		}
		if (value instanceof Instant instant) {
			return instant;
		}
		throw new IllegalArgumentException("Unsupported timestamp type: " + value);
	}
}
