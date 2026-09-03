package com.motodigo.core.support.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.support.entity.ModerationActionRecord;
import com.motodigo.core.support.entity.SupportTicketMessageRecord;
import com.motodigo.core.support.entity.SupportTicketRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class SupportTicketRepository {

	private final JdbcTemplate jdbcTemplate;

	public SupportTicketRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public SupportTicketRecord insert(UUID userId, String ticketNumber, String category, String priority,
			String subject, String message, UUID tripId, UUID bookingId, UUID paymentTransactionId,
			UUID offlineOperationId) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO support_tickets (
				  user_id, ticket_number, category, priority, status, subject, message,
				  trip_id, booking_id, payment_transaction_id, offline_operation_id)
				VALUES (?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?)
				RETURNING *
				""", this::mapTicket, userId, ticketNumber, category, priority, subject, message, tripId, bookingId,
				paymentTransactionId, offlineOperationId);
	}

	public Optional<SupportTicketRecord> findById(UUID id) {
		return this.jdbcTemplate.query("""
				SELECT * FROM support_tickets WHERE id = ?
				""", this::mapTicket, id).stream().findFirst();
	}

	public Optional<SupportTicketRecord> findByIdForUser(UUID id, UUID userId) {
		return this.jdbcTemplate.query("""
				SELECT * FROM support_tickets WHERE id = ? AND user_id = ?
				""", this::mapTicket, id, userId).stream().findFirst();
	}

	public List<SupportTicketRecord> listForUser(UUID userId, String status, int limit, int offset) {
		if (status == null || status.isBlank()) {
			return this.jdbcTemplate.query("""
					SELECT * FROM support_tickets
					WHERE user_id = ?
					ORDER BY created_at DESC
					LIMIT ? OFFSET ?
					""", this::mapTicket, userId, limit, offset);
		}
		return this.jdbcTemplate.query("""
				SELECT * FROM support_tickets
				WHERE user_id = ? AND status = ?
				ORDER BY created_at DESC
				LIMIT ? OFFSET ?
				""", this::mapTicket, userId, status.trim().toUpperCase(Locale.ROOT), limit, offset);
	}

	public List<SupportTicketRecord> listForAdmin(String category, String priority, String status, int limit,
			int offset) {
		StringBuilder sql = new StringBuilder("""
				SELECT * FROM support_tickets WHERE 1=1
				""");
		List<Object> args = new ArrayList<>();
		if (category != null && !category.isBlank()) {
			sql.append(" AND category = ?");
			args.add(category.trim().toUpperCase(Locale.ROOT));
		}
		if (priority != null && !priority.isBlank()) {
			sql.append(" AND priority = ?");
			args.add(priority.trim().toUpperCase(Locale.ROOT));
		}
		if (status != null && !status.isBlank()) {
			sql.append(" AND status = ?");
			args.add(status.trim().toUpperCase(Locale.ROOT));
		}
		sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);
		return this.jdbcTemplate.query(sql.toString(), this::mapTicket, args.toArray());
	}

	public List<SupportTicketRecord> listOpenForModeration(int limit, int offset) {
		return this.jdbcTemplate.query("""
				SELECT * FROM support_tickets
				WHERE status IN ('OPEN', 'IN_PROGRESS', 'WAITING_USER')
				ORDER BY
				  CASE priority WHEN 'URGENT' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END,
				  created_at ASC
				LIMIT ? OFFSET ?
				""", this::mapTicket, limit, offset);
	}

	public SupportTicketRecord updateStatus(UUID id, String status, Instant closedAt) {
		return this.jdbcTemplate.queryForObject("""
				UPDATE support_tickets
				SET status = ?,
				    closed_at = CASE
				      WHEN ? IN ('RESOLVED', 'CLOSED') THEN COALESCE(?, closed_at, now())
				      ELSE closed_at
				    END
				WHERE id = ?
				RETURNING *
				""", this::mapTicket, status, status,
				closedAt == null ? null : Timestamp.from(closedAt), id);
	}

	public SupportTicketMessageRecord insertMessage(UUID ticketId, UUID authorUserId, UUID authorAdminProfileId,
			String body) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO support_ticket_messages (
				  support_ticket_id, author_user_id, author_admin_profile_id, body)
				VALUES (?, ?, ?, ?)
				RETURNING *
				""", this::mapMessage, ticketId, authorUserId, authorAdminProfileId, body);
	}

	public List<SupportTicketMessageRecord> listMessages(UUID ticketId) {
		return this.jdbcTemplate.query("""
				SELECT * FROM support_ticket_messages
				WHERE support_ticket_id = ?
				ORDER BY created_at ASC
				""", this::mapMessage, ticketId);
	}

	public ModerationActionRecord insertModerationAction(UUID adminProfileId, String targetType, UUID targetId,
			String action, String reason) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO moderation_actions (admin_profile_id, target_type, target_id, action, reason)
				VALUES (?, ?, ?, ?, ?)
				RETURNING *
				""", this::mapAction, adminProfileId, targetType, targetId, action, reason);
	}

	private SupportTicketRecord mapTicket(ResultSet rs, int rowNum) throws SQLException {
		return new SupportTicketRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getObject("assigned_admin_profile_id", UUID.class),
				rs.getString("ticket_number"),
				rs.getString("category"),
				rs.getString("priority"),
				rs.getString("status"),
				rs.getString("subject"),
				rs.getString("message"),
				rs.getObject("trip_id", UUID.class),
				rs.getObject("booking_id", UUID.class),
				rs.getObject("payment_transaction_id", UUID.class),
				rs.getObject("offline_operation_id", UUID.class),
				toInstant(rs.getObject("created_at")),
				toInstantNullable(rs.getObject("closed_at")));
	}

	private SupportTicketMessageRecord mapMessage(ResultSet rs, int rowNum) throws SQLException {
		return new SupportTicketMessageRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("support_ticket_id", UUID.class),
				rs.getObject("author_user_id", UUID.class),
				rs.getObject("author_admin_profile_id", UUID.class),
				rs.getString("body"),
				toInstant(rs.getObject("created_at")));
	}

	private ModerationActionRecord mapAction(ResultSet rs, int rowNum) throws SQLException {
		return new ModerationActionRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("admin_profile_id", UUID.class),
				rs.getString("target_type"),
				rs.getObject("target_id", UUID.class),
				rs.getString("action"),
				rs.getString("reason"),
				toInstant(rs.getObject("acted_at")));
	}

	private static Instant toInstant(Object value) {
		Instant instant = toInstantNullable(value);
		if (instant == null) {
			throw new IllegalArgumentException("Timestamp required");
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
		throw new IllegalArgumentException("Unsupported timestamp: " + value);
	}
}
