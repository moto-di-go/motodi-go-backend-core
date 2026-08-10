package com.motodigo.core.intercity.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.intercity.dto.AnnouncementSearchCriteria;
import com.motodigo.core.intercity.dto.GeoPointRequest;
import com.motodigo.core.intercity.entity.AnnouncementSort;
import com.motodigo.core.intercity.entity.AnnouncementStatus;
import com.motodigo.core.intercity.entity.IntercityAnnouncementRecord;
import com.motodigo.core.intercity.support.AccentFold;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class IntercityAnnouncementRepository {

	private static final String SELECT_BASE = """
			SELECT id, publisher_profile_id, vehicle_id, route_id, departure_city, destination_city,
			       departure_quarter, departure_date, departure_time, price_fcfa, total_seats, remaining_seats,
			       instructions, status, created_at, updated_at
			FROM intercity_announcements
			""";

	private final JdbcTemplate jdbcTemplate;

	public IntercityAnnouncementRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public IntercityAnnouncementRecord insert(UUID publisherProfileId, UUID vehicleId, String departureCity,
			String destinationCity, String departureQuarter, GeoPointRequest departurePoint,
			GeoPointRequest destinationPoint, LocalDate departureDate, LocalTime departureTime, BigDecimal priceFcfa,
			int totalSeats, String instructions, AnnouncementStatus status) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO intercity_announcements (
				  publisher_profile_id, vehicle_id, departure_city, destination_city, departure_quarter,
				  departure_point_geom, destination_point_geom, departure_date, departure_time,
				  price_fcfa, total_seats, remaining_seats, instructions, status
				) VALUES (
				  ?, ?, ?, ?, ?,
				  CASE WHEN ? IS NULL THEN NULL ELSE ST_SetSRID(ST_MakePoint(?, ?), 4326) END,
				  CASE WHEN ? IS NULL THEN NULL ELSE ST_SetSRID(ST_MakePoint(?, ?), 4326) END,
				  ?, ?, ?, ?, ?, ?, ?
				)
				RETURNING id, publisher_profile_id, vehicle_id, route_id, departure_city, destination_city,
				          departure_quarter, departure_date, departure_time, price_fcfa, total_seats, remaining_seats,
				          instructions, status, created_at, updated_at
				""", this::mapAnnouncement,
				publisherProfileId,
				vehicleId,
				departureCity,
				destinationCity,
				departureQuarter,
				departurePoint == null ? null : Boolean.TRUE,
				departurePoint == null ? null : departurePoint.longitude(),
				departurePoint == null ? null : departurePoint.latitude(),
				destinationPoint == null ? null : Boolean.TRUE,
				destinationPoint == null ? null : destinationPoint.longitude(),
				destinationPoint == null ? null : destinationPoint.latitude(),
				Date.valueOf(departureDate),
				Time.valueOf(departureTime),
				priceFcfa,
				totalSeats,
				totalSeats,
				instructions,
				status.name());
	}

	public Optional<IntercityAnnouncementRecord> findById(UUID id) {
		return this.jdbcTemplate.query(SELECT_BASE + " WHERE id = ?", this::mapAnnouncement, id).stream().findFirst();
	}

	public Optional<double[]> findDepartureCoords(UUID id) {
		return this.jdbcTemplate.query("""
				SELECT ST_Y(departure_point_geom::geometry) AS lat, ST_X(departure_point_geom::geometry) AS lon
				FROM intercity_announcements
				WHERE id = ?
				  AND departure_point_geom IS NOT NULL
				""", (rs, rowNum) -> new double[] { rs.getDouble("lat"), rs.getDouble("lon") }, id).stream().findFirst();
	}

	public boolean existsPublishedOrFullWithVehicle(UUID vehicleId) {
		Boolean exists = this.jdbcTemplate.queryForObject("""
				SELECT EXISTS (
				  SELECT 1 FROM intercity_announcements
				  WHERE vehicle_id = ?
				    AND status IN ('PUBLISHED', 'FULL')
				)
				""", Boolean.class, vehicleId);
		return Boolean.TRUE.equals(exists);
	}

	/**
	 * Acquires a pessimistic row lock on the announcement for the surrounding transaction so
	 * concurrent seat locks serialize on {@code remaining_seats}.
	 */
	public Optional<IntercityAnnouncementRecord> lockByIdForUpdate(UUID id) {
		return this.jdbcTemplate.query(SELECT_BASE + " WHERE id = ? FOR UPDATE", this::mapAnnouncement, id).stream()
				.findFirst();
	}

	public IntercityAnnouncementRecord decrementRemainingSeats(UUID id, int seats) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE intercity_announcements
				SET remaining_seats = remaining_seats - ?,
				    status = CASE WHEN remaining_seats - ? = 0 THEN 'FULL' ELSE status END,
				    updated_at = now()
				WHERE id = ?
				  AND remaining_seats >= ?
				RETURNING id, publisher_profile_id, vehicle_id, route_id, departure_city, destination_city,
				          departure_quarter, departure_date, departure_time, price_fcfa, total_seats, remaining_seats,
				          instructions, status, created_at, updated_at
				""", this::mapAnnouncement, seats, seats, id, seats);
	}

	public IntercityAnnouncementRecord incrementRemainingSeats(UUID id, int seats) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE intercity_announcements
				SET remaining_seats = remaining_seats + ?,
				    status = CASE
				               WHEN status = 'FULL' AND remaining_seats + ? > 0 THEN 'PUBLISHED'
				               ELSE status
				             END,
				    updated_at = now()
				WHERE id = ?
				RETURNING id, publisher_profile_id, vehicle_id, route_id, departure_city, destination_city,
				          departure_quarter, departure_date, departure_time, price_fcfa, total_seats, remaining_seats,
				          instructions, status, created_at, updated_at
				""", this::mapAnnouncement, seats, seats, id);
	}

	public List<IntercityAnnouncementRecord> findByPublisher(UUID publisherProfileId, int limit, int offset) {
		return this.jdbcTemplate.query(SELECT_BASE + """
				WHERE publisher_profile_id = ?
				ORDER BY departure_date DESC, departure_time DESC, created_at DESC
				LIMIT ? OFFSET ?
				""", this::mapAnnouncement, publisherProfileId, limit, offset);
	}

	public boolean existsBookingForAnnouncement(UUID announcementId) {
		Boolean exists = this.jdbcTemplate.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM bookings WHERE announcement_id = ?)
				""", Boolean.class, announcementId);
		return Boolean.TRUE.equals(exists);
	}

	public IntercityAnnouncementRecord update(UUID id, String departureCity, String destinationCity,
			String departureQuarter, GeoPointRequest departurePoint, GeoPointRequest destinationPoint,
			LocalDate departureDate, LocalTime departureTime, BigDecimal priceFcfa, int totalSeats, int remainingSeats,
			UUID vehicleId, String instructions) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE intercity_announcements
				SET departure_city = ?,
				    destination_city = ?,
				    departure_quarter = ?,
				    departure_point_geom = CASE WHEN ? IS NULL THEN departure_point_geom
				                               ELSE ST_SetSRID(ST_MakePoint(?, ?), 4326) END,
				    destination_point_geom = CASE WHEN ? IS NULL THEN destination_point_geom
				                                  ELSE ST_SetSRID(ST_MakePoint(?, ?), 4326) END,
				    departure_date = ?,
				    departure_time = ?,
				    price_fcfa = ?,
				    total_seats = ?,
				    remaining_seats = ?,
				    vehicle_id = ?,
				    instructions = ?,
				    updated_at = now()
				WHERE id = ?
				RETURNING id, publisher_profile_id, vehicle_id, route_id, departure_city, destination_city,
				          departure_quarter, departure_date, departure_time, price_fcfa, total_seats, remaining_seats,
				          instructions, status, created_at, updated_at
				""", this::mapAnnouncement,
				departureCity,
				destinationCity,
				departureQuarter,
				departurePoint == null ? null : Boolean.TRUE,
				departurePoint == null ? null : departurePoint.longitude(),
				departurePoint == null ? null : departurePoint.latitude(),
				destinationPoint == null ? null : Boolean.TRUE,
				destinationPoint == null ? null : destinationPoint.longitude(),
				destinationPoint == null ? null : destinationPoint.latitude(),
				Date.valueOf(departureDate),
				Time.valueOf(departureTime),
				priceFcfa,
				totalSeats,
				remainingSeats,
				vehicleId,
				instructions,
				id);
	}

	public IntercityAnnouncementRecord updateStatus(UUID id, AnnouncementStatus status) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE intercity_announcements
				SET status = ?, updated_at = now()
				WHERE id = ?
				RETURNING id, publisher_profile_id, vehicle_id, route_id, departure_city, destination_city,
				          departure_quarter, departure_date, departure_time, price_fcfa, total_seats, remaining_seats,
				          instructions, status, created_at, updated_at
				""", this::mapAnnouncement, status.name(), id);
	}

	public List<IntercityAnnouncementRecord> search(AnnouncementSearchCriteria criteria) {
		StringBuilder sql = new StringBuilder("""
				SELECT a.id, a.publisher_profile_id, a.vehicle_id, a.route_id, a.departure_city, a.destination_city,
				       a.departure_quarter, a.departure_date, a.departure_time, a.price_fcfa, a.total_seats,
				       a.remaining_seats, a.instructions, a.status, a.created_at, a.updated_at
				FROM intercity_announcements a
				INNER JOIN profiles p ON p.id = a.publisher_profile_id
				WHERE p.validation_status = 'VALIDATED'
				  AND a.status IN ('PUBLISHED', 'FULL')
				""");
		List<Object> params = new ArrayList<>();
		appendSearchFilters(sql, params, criteria);
		appendOrderBy(sql, params, criteria);
		sql.append(" LIMIT ? OFFSET ?");
		params.add(criteria.size() + 1);
		params.add(criteria.page() * criteria.size());
		return this.jdbcTemplate.query(sql.toString(), this::mapAnnouncement, params.toArray());
	}

	private static void appendSearchFilters(StringBuilder sql, List<Object> params, AnnouncementSearchCriteria criteria) {
		// Comparaison case/accent-insensitive via translate SQL + fold Java (sans extension unaccent).
		if (criteria.departureCity() != null) {
			sql.append(" AND ").append(foldCitySql("a.departure_city")).append(" = ?");
			params.add(AccentFold.fold(criteria.departureCity()));
		}
		if (criteria.destinationCity() != null) {
			sql.append(" AND ").append(foldCitySql("a.destination_city")).append(" = ?");
			params.add(AccentFold.fold(criteria.destinationCity()));
		}
		if (criteria.date() != null) {
			sql.append(" AND a.departure_date = ?");
			params.add(Date.valueOf(criteria.date()));
		}
		if (criteria.maxPrice() != null) {
			sql.append(" AND a.price_fcfa <= ?");
			params.add(criteria.maxPrice());
		}
		if (criteria.timeFrom() != null) {
			sql.append(" AND a.departure_time >= ?");
			params.add(Time.valueOf(criteria.timeFrom()));
		}
		if (criteria.timeTo() != null) {
			sql.append(" AND a.departure_time <= ?");
			params.add(Time.valueOf(criteria.timeTo()));
		}
		if (criteria.quarter() != null) {
			sql.append(" AND ").append(foldCitySql("a.departure_quarter")).append(" = ?");
			params.add(AccentFold.fold(criteria.quarter()));
		}
		if (criteria.onlyAvailable()) {
			sql.append(" AND a.remaining_seats > 0 AND a.status = 'PUBLISHED'");
		}
	}

	/** Expr SQL : lower + suppression accents FR courants. */
	private static String foldCitySql(String column) {
		return "translate(lower(" + column + "), "
				+ "'àáâãäåèéêëìíîïòóôõöùúûüýÿçñ',"
				+ "'aaaaaaeeeeiiiiooooouuuuyycn')";
	}

	private static void appendOrderBy(StringBuilder sql, List<Object> params, AnnouncementSearchCriteria criteria) {
		AnnouncementSort sort = criteria.sort() == null ? AnnouncementSort.departureTime : criteria.sort();
		if (sort == AnnouncementSort.price) {
			sql.append(" ORDER BY a.price_fcfa ASC, a.departure_date ASC, a.departure_time ASC");
			return;
		}
		if (sort == AnnouncementSort.proximity && criteria.nearLat() != null && criteria.nearLon() != null) {
			sql.append("""
					 ORDER BY a.departure_point_geom <-> ST_SetSRID(ST_MakePoint(?, ?), 4326) ASC NULLS LAST,
					          a.departure_date ASC, a.departure_time ASC
					""");
			params.add(criteria.nearLon());
			params.add(criteria.nearLat());
			return;
		}
		sql.append(" ORDER BY a.departure_date ASC, a.departure_time ASC");
	}

	private IntercityAnnouncementRecord mapAnnouncement(ResultSet rs, int ignoredRowNum) throws SQLException {
		return new IntercityAnnouncementRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("publisher_profile_id", UUID.class),
				rs.getObject("vehicle_id", UUID.class),
				rs.getObject("route_id", UUID.class),
				rs.getString("departure_city"),
				rs.getString("destination_city"),
				rs.getString("departure_quarter"),
				rs.getDate("departure_date").toLocalDate(),
				rs.getTime("departure_time").toLocalTime(),
				rs.getBigDecimal("price_fcfa"),
				rs.getInt("total_seats"),
				rs.getInt("remaining_seats"),
				rs.getString("instructions"),
				AnnouncementStatus.valueOf(rs.getString("status")),
				toInstant(rs.getObject("created_at")),
				toInstant(rs.getObject("updated_at")));
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
