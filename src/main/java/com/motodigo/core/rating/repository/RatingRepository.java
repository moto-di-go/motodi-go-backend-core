package com.motodigo.core.rating.repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.rating.entity.RatingRecord;
import com.motodigo.core.rating.entity.RatingStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class RatingRepository {

	private final JdbcTemplate jdbcTemplate;

	public RatingRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public RatingRecord insert(UUID tripId, UUID raterUserId, UUID ratedProfileId, int stars, String comment,
			List<String> tags, RatingStatus status) {
		String[] tagArray = tags == null ? new String[0] : tags.toArray(String[]::new);
		return this.jdbcTemplate.execute((Connection connection) -> {
			Array sqlTags = connection.createArrayOf("text", tagArray);
			try (var ps = connection.prepareStatement("""
					INSERT INTO ratings (trip_id, rater_user_id, rated_profile_id, stars, comment, tags, status)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					RETURNING *
					""")) {
				ps.setObject(1, tripId);
				ps.setObject(2, raterUserId);
				ps.setObject(3, ratedProfileId);
				ps.setInt(4, stars);
				ps.setString(5, comment);
				ps.setArray(6, sqlTags);
				ps.setString(7, status.name());
				try (var rs = ps.executeQuery()) {
					if (!rs.next()) {
						throw new IllegalStateException("Insert rating returned no row");
					}
					return map(rs, 1);
				}
			}
		});
	}

	public List<RatingRecord> findByTripAndRater(UUID tripId, UUID raterUserId) {
		return this.jdbcTemplate.query("""
				SELECT * FROM ratings
				WHERE trip_id = ? AND rater_user_id = ?
				ORDER BY created_at DESC
				""", this::map, tripId, raterUserId);
	}

	public Optional<RatingRecord> findByTripRaterRated(UUID tripId, UUID raterUserId, UUID ratedProfileId) {
		return this.jdbcTemplate.query("""
				SELECT * FROM ratings
				WHERE trip_id = ? AND rater_user_id = ? AND rated_profile_id = ?
				""", this::map, tripId, raterUserId, ratedProfileId).stream().findFirst();
	}

	public boolean existsForTripAndRater(UUID tripId, UUID raterUserId) {
		Boolean exists = this.jdbcTemplate.queryForObject("""
				SELECT EXISTS(
				  SELECT 1 FROM ratings WHERE trip_id = ? AND rater_user_id = ?
				)
				""", Boolean.class, tripId, raterUserId);
		return Boolean.TRUE.equals(exists);
	}

	public BigDecimal recalculateAverageForProfile(UUID profileId) {
		BigDecimal average = this.jdbcTemplate.queryForObject("""
				SELECT COALESCE(AVG(stars)::numeric(3,2), 0)
				FROM ratings
				WHERE rated_profile_id = ? AND status IN ('PUBLISHED', 'FLAGGED')
				""", BigDecimal.class, profileId);
		this.jdbcTemplate.update("""
				UPDATE profiles
				SET average_rating = ?, updated_at = now()
				WHERE id = ?
				""", average, profileId);
		return average == null ? BigDecimal.ZERO : average;
	}

	public RatingRecord updateStatus(UUID ratingId, RatingStatus status) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE ratings SET status = ? WHERE id = ?
				RETURNING *
				""", this::map, status.name(), ratingId);
	}

	public Optional<RatingRecord> findById(UUID ratingId) {
		return this.jdbcTemplate.query("""
				SELECT * FROM ratings WHERE id = ?
				""", this::map, ratingId).stream().findFirst();
	}

	public List<RatingRecord> findFlagged(int limit, int offset) {
		return this.jdbcTemplate.query("""
				SELECT * FROM ratings
				WHERE status = 'FLAGGED'
				ORDER BY created_at ASC
				LIMIT ? OFFSET ?
				""", this::map, limit, offset);
	}

	public int countFlagged() {
		Integer count = this.jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM ratings WHERE status = 'FLAGGED'
				""", Integer.class);
		return count == null ? 0 : count;
	}

	private RatingRecord map(ResultSet rs, int rowNum) throws SQLException {
		return new RatingRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("trip_id", UUID.class),
				rs.getObject("rater_user_id", UUID.class),
				rs.getObject("rated_profile_id", UUID.class),
				rs.getInt("stars"),
				rs.getString("comment"),
				tags(rs.getArray("tags")),
				RatingStatus.valueOf(rs.getString("status")),
				toInstant(rs.getObject("created_at")));
	}

	private static List<String> tags(Array sqlArray) throws SQLException {
		if (sqlArray == null) {
			return List.of();
		}
		Object value = sqlArray.getArray();
		if (value instanceof String[] strings) {
			return List.of(strings);
		}
		if (value instanceof Object[] objects) {
			return Arrays.stream(objects).map(String::valueOf).toList();
		}
		return List.of();
	}

	private static Instant toInstant(Object value) {
		if (value instanceof OffsetDateTime odt) {
			return odt.toInstant();
		}
		if (value instanceof Instant instant) {
			return instant;
		}
		throw new IllegalArgumentException("Unsupported timestamp: " + value);
	}
}
