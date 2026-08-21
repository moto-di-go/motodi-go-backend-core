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
