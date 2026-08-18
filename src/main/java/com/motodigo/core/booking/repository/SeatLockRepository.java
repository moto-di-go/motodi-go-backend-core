package com.motodigo.core.booking.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.booking.entity.SeatLockRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class SeatLockRepository {

	private static final String SELECT_BASE = """
			SELECT id, announcement_id, booking_id, seats_locked, locked_at, expires_at, consumed
			FROM seat_locks
			""";

	private final JdbcTemplate jdbcTemplate;

	public SeatLockRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public SeatLockRecord insert(UUID announcementId, UUID bookingId, int seatsLocked, Instant expiresAt) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO seat_locks (announcement_id, booking_id, seats_locked, expires_at)
				VALUES (?, ?, ?, ?)
				RETURNING id, announcement_id, booking_id, seats_locked, locked_at, expires_at, consumed
				""", this::mapLock, announcementId, bookingId, seatsLocked, Timestamp.from(expiresAt));
	}

	public Optional<SeatLockRecord> findByBookingId(UUID bookingId) {
		return this.jdbcTemplate.query(SELECT_BASE + " WHERE booking_id = ?", this::mapLock, bookingId).stream()
				.findFirst();
	}

	public Optional<SeatLockRecord> lockByBookingIdForUpdate(UUID bookingId) {
		return this.jdbcTemplate.query(SELECT_BASE + " WHERE booking_id = ? FOR UPDATE", this::mapLock, bookingId)
				.stream().findFirst();
	}

	public SeatLockRecord markConsumed(UUID id) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE seat_locks
				SET consumed = true
				WHERE id = ?
				RETURNING id, announcement_id, booking_id, seats_locked, locked_at, expires_at, consumed
				""", this::mapLock, id);
	}

	public List<SeatLockRecord> findExpiredUnconsumed(Instant now, int limit) {
		return this.jdbcTemplate.query(SELECT_BASE + """
				WHERE consumed = false
				  AND expires_at < ?
				ORDER BY expires_at ASC
				LIMIT ?
				""", this::mapLock, Timestamp.from(now), limit);
	}

	private SeatLockRecord mapLock(ResultSet rs, int rowNum) throws SQLException {
		return new SeatLockRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("announcement_id", UUID.class),
				rs.getObject("booking_id", UUID.class),
				rs.getInt("seats_locked"),
				toInstant(rs.getObject("locked_at")),
				toInstant(rs.getObject("expires_at")),
				rs.getBoolean("consumed"));
	}

	private static Instant toInstant(Object value) {
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
