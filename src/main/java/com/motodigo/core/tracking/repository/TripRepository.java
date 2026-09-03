package com.motodigo.core.tracking.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.tracking.entity.TripEventRecord;
import com.motodigo.core.tracking.entity.TripRecord;
import com.motodigo.core.tracking.entity.TripStatus;
import com.motodigo.core.tracking.entity.TripType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class TripRepository {

	private static final ZoneId APP_ZONE = ZoneId.of("Africa/Douala");

	private static final String SELECT_TRIP = """
			SELECT t.id, t.type, t.status, t.passenger_profile_id, t.driver_profile_id, t.transporter_profile_id,
			       t.route_id, t.fare_estimate_id,
			       ST_Y(t.origin_geom::geometry) AS origin_lat,
			       ST_X(t.origin_geom::geometry) AS origin_lon,
			       ST_Y(t.destination_geom::geometry) AS destination_lat,
			       ST_X(t.destination_geom::geometry) AS destination_lon,
			       t.planned_departure_at, t.started_at, t.completed_at, t.final_price_fcfa,
			       t.cancellation_reason, t.created_at,
			       b.id AS booking_id,
			       a.publisher_profile_id AS announcement_publisher_profile_id,
			       it.departure_city, it.destination_city, it.pt_ramassage
			FROM trips t
			LEFT JOIN LATERAL (
			  SELECT id, announcement_id
			  FROM bookings
			  WHERE trip_id = t.id
			  ORDER BY requested_at DESC
			  LIMIT 1
			) b ON true
			LEFT JOIN intercity_announcements a ON a.id = b.announcement_id
			LEFT JOIN intercity_trips it ON it.trip_id = t.id
			""";

	private final JdbcTemplate jdbcTemplate;

	public TripRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public TripRecord insertIntercityTrip(UUID passengerProfileId, UUID transporterProfileId, UUID routeId,
			Instant plannedDepartureAt, BigDecimal finalPriceFcfa, String departureCity, String destinationCity,
			String ptRamassage, int availableSeats) {
		TripRecord trip = this.jdbcTemplate.queryForObject("""
				INSERT INTO trips (
				  type, status, passenger_profile_id, transporter_profile_id, route_id,
				  planned_departure_at, final_price_fcfa
				)
				VALUES ('INTERCITY', 'CONFIRMED', ?, ?, ?, ?, ?)
				RETURNING id, type, status, passenger_profile_id, driver_profile_id, transporter_profile_id,
				          route_id, fare_estimate_id,
				          ST_Y(origin_geom::geometry) AS origin_lat,
				          ST_X(origin_geom::geometry) AS origin_lon,
				          ST_Y(destination_geom::geometry) AS destination_lat,
				          ST_X(destination_geom::geometry) AS destination_lon,
				          planned_departure_at, started_at, completed_at, final_price_fcfa,
				          cancellation_reason, created_at
				""", this::mapTripCore, passengerProfileId, transporterProfileId, routeId,
				plannedDepartureAt == null ? null : Timestamp.from(plannedDepartureAt), finalPriceFcfa);

		this.jdbcTemplate.update("""
				INSERT INTO intercity_trips (trip_id, departure_city, destination_city, pt_ramassage, available_seats)
				VALUES (?, ?, ?, ?, ?)
				""", trip.id(), departureCity, destinationCity, ptRamassage, Math.max(availableSeats, 0));

		return findById(trip.id()).orElse(trip);
	}

	public Optional<TripRecord> findById(UUID tripId) {
		return this.jdbcTemplate.query(SELECT_TRIP + " WHERE t.id = ?", this::mapTrip, tripId).stream().findFirst();
	}

	public Optional<TripRecord> lockByIdForUpdate(UUID tripId) {
		return this.jdbcTemplate.query(SELECT_TRIP + " WHERE t.id = ? FOR UPDATE OF t", this::mapTrip, tripId)
				.stream().findFirst();
	}

	public Optional<TripRecord> findByBookingId(UUID bookingId) {
		return this.jdbcTemplate.query("""
				SELECT t.id, t.type, t.status, t.passenger_profile_id, t.driver_profile_id, t.transporter_profile_id,
				       t.route_id, t.fare_estimate_id,
				       ST_Y(t.origin_geom::geometry) AS origin_lat,
				       ST_X(t.origin_geom::geometry) AS origin_lon,
				       ST_Y(t.destination_geom::geometry) AS destination_lat,
				       ST_X(t.destination_geom::geometry) AS destination_lon,
				       t.planned_departure_at, t.started_at, t.completed_at, t.final_price_fcfa,
				       t.cancellation_reason, t.created_at,
				       b.id AS booking_id,
				       a.publisher_profile_id AS announcement_publisher_profile_id,
				       it.departure_city, it.destination_city, it.pt_ramassage
				FROM bookings b
				INNER JOIN trips t ON t.id = b.trip_id
				LEFT JOIN intercity_announcements a ON a.id = b.announcement_id
				LEFT JOIN intercity_trips it ON it.trip_id = t.id
				WHERE b.id = ?
				""", this::mapTrip, bookingId).stream().findFirst();
	}

	public List<TripRecord> findActiveForProfile(UUID profileId) {
		return this.jdbcTemplate.query(SELECT_TRIP + """
				WHERE t.status IN ('CONFIRMED', 'APPROACHING', 'ON_BOARD')
				  AND (
				    t.passenger_profile_id = ?
				    OR t.driver_profile_id = ?
				    OR t.transporter_profile_id = ?
				    OR a.publisher_profile_id = ?
				  )
				ORDER BY t.planned_departure_at NULLS LAST, t.created_at DESC
				""", this::mapTrip, profileId, profileId, profileId, profileId);
	}

	public TripRecord updateStatus(UUID tripId, TripStatus status, Instant startedAt, Instant completedAt,
			String cancellationReason) {
		this.jdbcTemplate.update("""
				UPDATE trips
				SET status = ?,
				    started_at = COALESCE(?, started_at),
				    completed_at = COALESCE(?, completed_at),
				    cancellation_reason = COALESCE(?, cancellation_reason)
				WHERE id = ?
				""", status.name(),
				startedAt == null ? null : Timestamp.from(startedAt),
				completedAt == null ? null : Timestamp.from(completedAt),
				cancellationReason,
				tripId);
		return findById(tripId).orElseThrow();
	}

	public void attachBooking(UUID bookingId, UUID tripId) {
		this.jdbcTemplate.update("""
				UPDATE bookings
				SET trip_id = ?
				WHERE id = ? AND trip_id IS NULL
				""", tripId, bookingId);
	}

	public TripEventRecord insertEvent(UUID tripId, TripStatus status, String label, String source,
			Instant occurredAt) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO trip_events (trip_id, status, label, occurred_at, source)
				VALUES (?, ?, ?, ?, ?)
				RETURNING id, trip_id, status, label, occurred_at, source
				""", this::mapEvent, tripId, status.name(), label,
				Timestamp.from(occurredAt), source);
	}

	public List<TripEventRecord> findEventsByTripId(UUID tripId) {
		return this.jdbcTemplate.query("""
				SELECT id, trip_id, status, label, occurred_at, source
				FROM trip_events
				WHERE trip_id = ?
				ORDER BY occurred_at ASC, id ASC
				""", this::mapEvent, tripId);
	}

	public List<TripHistoryRow> findHistoryForProfile(UUID profileId, UUID userId, String tab, Instant from, Instant to,
			String status, String paymentStatus, String tripType, int limit, int offset) {
		StringBuilder sql = new StringBuilder("""
				SELECT * FROM (
				  SELECT t.id AS id,
				         'TRIP' AS kind,
				         t.type AS trip_type,
				         t.status AS status,
				         COALESCE(it.departure_city, '') AS origin,
				         COALESCE(it.destination_city, '') AS destination,
				         t.final_price_fcfa AS amount,
				         (
				           SELECT pt.status FROM payment_transactions pt
				           WHERE pt.trip_id = t.id OR pt.booking_id = b.id
				           ORDER BY pt.initiated_at DESC
				           LIMIT 1
				         ) AS payment_status,
				         (
				           SELECT r.id FROM receipts r
				           INNER JOIN payment_transactions pt ON pt.id = r.payment_transaction_id
				           WHERE pt.trip_id = t.id OR pt.booking_id = b.id
				           ORDER BY r.issued_at DESC
				           LIMIT 1
				         ) AS receipt_id,
				         EXISTS(
				           SELECT 1 FROM ratings rt
				           WHERE rt.trip_id = t.id AND rt.rater_user_id = ?
				         ) AS rating_submitted,
				         t.created_at AS created_at,
				         t.completed_at AS completed_at,
				         t.planned_departure_at AS planned_at
				  FROM trips t
				  LEFT JOIN LATERAL (
				    SELECT id FROM bookings WHERE trip_id = t.id ORDER BY requested_at DESC LIMIT 1
				  ) b ON true
				  LEFT JOIN intercity_trips it ON it.trip_id = t.id
				  WHERE (
				    t.passenger_profile_id = ?
				    OR t.driver_profile_id = ?
				    OR t.transporter_profile_id = ?
				  )
				  UNION ALL
				  SELECT b.id AS id,
				         'BOOKING' AS kind,
				         'INTERCITY' AS trip_type,
				         b.status AS status,
				         a.departure_city AS origin,
				         a.destination_city AS destination,
				         (a.price_fcfa * b.seats_requested) AS amount,
				         (
				           SELECT pt.status FROM payment_transactions pt
				           WHERE pt.booking_id = b.id
				           ORDER BY pt.initiated_at DESC
				           LIMIT 1
				         ) AS payment_status,
				         (
				           SELECT r.id FROM receipts r
				           INNER JOIN payment_transactions pt ON pt.id = r.payment_transaction_id
				           WHERE pt.booking_id = b.id
				           ORDER BY r.issued_at DESC
				           LIMIT 1
				         ) AS receipt_id,
				         false AS rating_submitted,
				         b.requested_at AS created_at,
				         NULL::timestamptz AS completed_at,
				         NULL::timestamptz AS planned_at
				  FROM bookings b
				  INNER JOIN intercity_announcements a ON a.id = b.announcement_id
				  WHERE b.passenger_profile_id = ?
				    AND b.trip_id IS NULL
				) h
				WHERE 1=1
				""");

		java.util.ArrayList<Object> args = new java.util.ArrayList<>();
		args.add(userId);
		args.add(profileId);
		args.add(profileId);
		args.add(profileId);
		args.add(profileId);

		String normalizedTab = tab == null || tab.isBlank() ? "PAST" : tab.trim().toUpperCase();
		switch (normalizedTab) {
			case "UPCOMING" -> sql.append("""
					 AND h.completed_at IS NULL
					 AND h.status IN ('CONFIRMED', 'APPROACHING', 'ON_BOARD', 'PENDING', 'PENDING_PAYMENT', 'PAID')
					""");
			case "CANCELLED" -> sql.append(" AND h.status IN ('CANCELLED', 'EXPIRED')");
			default -> sql.append(" AND (h.status = 'COMPLETED' OR h.completed_at IS NOT NULL)");
		}

		if (from != null) {
			sql.append(" AND h.created_at >= ?");
			args.add(java.sql.Timestamp.from(from));
		}
		if (to != null) {
			sql.append(" AND h.created_at <= ?");
			args.add(java.sql.Timestamp.from(to));
		}
		if (status != null && !status.isBlank()) {
			sql.append(" AND h.status = ?");
			args.add(status.trim().toUpperCase());
		}
		if (paymentStatus != null && !paymentStatus.isBlank()) {
			sql.append(" AND h.payment_status = ?");
			args.add(paymentStatus.trim().toUpperCase());
		}
		if (tripType != null && !tripType.isBlank()) {
			sql.append(" AND h.trip_type = ?");
			args.add(tripType.trim().toUpperCase());
		}

		sql.append(" ORDER BY h.created_at DESC LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);

		return this.jdbcTemplate.query(sql.toString(), this::mapHistoryRow, args.toArray());
	}

	public record TripHistoryRow(
			UUID id,
			String kind,
			String tripType,
			String status,
			String origin,
			String destination,
			BigDecimal amount,
			String paymentStatus,
			UUID receiptId,
			boolean ratingSubmitted,
			Instant createdAt,
			Instant completedAt) {
	}

	private TripHistoryRow mapHistoryRow(ResultSet rs, int rowNum) throws SQLException {
		return new TripHistoryRow(
				rs.getObject("id", UUID.class),
				rs.getString("kind"),
				rs.getString("trip_type"),
				rs.getString("status"),
				rs.getString("origin"),
				rs.getString("destination"),
				rs.getBigDecimal("amount"),
				rs.getString("payment_status"),
				rs.getObject("receipt_id", UUID.class),
				rs.getBoolean("rating_submitted"),
				toInstant(rs.getObject("created_at")),
				toInstantNullable(rs.getObject("completed_at")));
	}

	public static Instant plannedDeparture(LocalDate departureDate, LocalTime departureTime) {
		if (departureDate == null || departureTime == null) {
			return null;
		}
		return LocalDateTime.of(departureDate, departureTime).atZone(APP_ZONE).toInstant();
	}

	private TripRecord mapTrip(ResultSet rs, int rowNum) throws SQLException {
		TripRecord core = mapTripCore(rs, rowNum);
		return new TripRecord(
				core.id(),
				core.type(),
				core.status(),
				core.passengerProfileId(),
				core.driverProfileId(),
				core.transporterProfileId(),
				core.routeId(),
				core.fareEstimateId(),
				core.originLat(),
				core.originLon(),
				core.destinationLat(),
				core.destinationLon(),
				core.plannedDepartureAt(),
				core.startedAt(),
				core.completedAt(),
				core.finalPriceFcfa(),
				core.cancellationReason(),
				core.createdAt(),
				rs.getObject("booking_id", UUID.class),
				rs.getObject("announcement_publisher_profile_id", UUID.class),
				rs.getString("departure_city"),
				rs.getString("destination_city"),
				rs.getString("pt_ramassage"));
	}

	private TripRecord mapTripCore(ResultSet rs, int rowNum) throws SQLException {
		return new TripRecord(
				rs.getObject("id", UUID.class),
				TripType.valueOf(rs.getString("type")),
				TripStatus.valueOf(rs.getString("status")),
				rs.getObject("passenger_profile_id", UUID.class),
				rs.getObject("driver_profile_id", UUID.class),
				rs.getObject("transporter_profile_id", UUID.class),
				rs.getObject("route_id", UUID.class),
				rs.getObject("fare_estimate_id", UUID.class),
				(Double) rs.getObject("origin_lat"),
				(Double) rs.getObject("origin_lon"),
				(Double) rs.getObject("destination_lat"),
				(Double) rs.getObject("destination_lon"),
				toInstantNullable(rs.getObject("planned_departure_at")),
				toInstantNullable(rs.getObject("started_at")),
				toInstantNullable(rs.getObject("completed_at")),
				rs.getBigDecimal("final_price_fcfa"),
				rs.getString("cancellation_reason"),
				toInstant(rs.getObject("created_at")),
				null,
				null,
				null,
				null,
				null);
	}

	private TripEventRecord mapEvent(ResultSet rs, int rowNum) throws SQLException {
		return new TripEventRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("trip_id", UUID.class),
				TripStatus.valueOf(rs.getString("status")),
				rs.getString("label"),
				toInstant(rs.getObject("occurred_at")),
				rs.getString("source"));
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
