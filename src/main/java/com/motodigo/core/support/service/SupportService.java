package com.motodigo.core.support.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.motodigo.core.notification.service.NotificationDispatchService;
import com.motodigo.core.rating.entity.RatingRecord;
import com.motodigo.core.rating.entity.RatingStatus;
import com.motodigo.core.rating.repository.RatingRepository;
import com.motodigo.core.support.dto.AdminSupportTicketUpdateRequest;
import com.motodigo.core.support.dto.ModerationActionRequest;
import com.motodigo.core.support.dto.ModerationActionResponse;
import com.motodigo.core.support.dto.ModerationQueueItem;
import com.motodigo.core.support.dto.ModerationQueuePageResponse;
import com.motodigo.core.support.dto.SupportTicketCreateRequest;
import com.motodigo.core.support.dto.SupportTicketDetailResponse;
import com.motodigo.core.support.dto.SupportTicketPageResponse;
import com.motodigo.core.support.dto.SupportTicketResponse;
import com.motodigo.core.support.entity.ModerationActionRecord;
import com.motodigo.core.support.entity.SupportTicketRecord;
import com.motodigo.core.support.exception.SupportErrorCode;
import com.motodigo.core.support.exception.SupportException;
import com.motodigo.core.support.repository.SupportTicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class SupportService {

	private static final String MODERATION_READ = "MODERATION_READ";
	private static final String MODERATION_WRITE = "MODERATION_WRITE";
	private static final Set<String> CATEGORIES = Set.of("TRANSPORT", "PAYMENT", "ACCOUNT", "SECURITY", "EMERGENCY");
	private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
	private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "WAITING_USER", "RESOLVED", "CLOSED");
	private static final Set<String> ACTIONS = Set.of("HIDE", "RESTORE", "ESCALATE", "CLOSE", "SUSPEND");
	private static final Set<String> TARGET_TYPES = Set.of("RATING", "SUPPORT_TICKET", "USER", "PROFILE",
			"ANNOUNCEMENT");

	private final SupportTicketRepository supportTicketRepository;
	private final RatingRepository ratingRepository;
	private final NotificationDispatchService notificationDispatchService;
	private final Clock clock;

	@Autowired
	public SupportService(SupportTicketRepository supportTicketRepository, RatingRepository ratingRepository,
			NotificationDispatchService notificationDispatchService) {
		this(supportTicketRepository, ratingRepository, notificationDispatchService, Clock.systemUTC());
	}

	SupportService(SupportTicketRepository supportTicketRepository, RatingRepository ratingRepository,
			NotificationDispatchService notificationDispatchService, Clock clock) {
		this.supportTicketRepository = supportTicketRepository;
		this.ratingRepository = ratingRepository;
		this.notificationDispatchService = notificationDispatchService;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	@Transactional
	public SupportTicketResponse createTicket(UUID userId, SupportTicketCreateRequest request) {
		validateCreate(request);
		String ticketNumber = "MDG-SUP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
		SupportTicketRecord created = this.supportTicketRepository.insert(
				userId,
				ticketNumber,
				request.category().trim().toUpperCase(Locale.ROOT),
				request.priority().trim().toUpperCase(Locale.ROOT),
				request.subject().trim(),
				request.message().trim(),
				request.tripId(),
				request.bookingId(),
				request.paymentTransactionId(),
				request.offlineOperationId());

		this.supportTicketRepository.insertMessage(created.id(), userId, null, created.message());
		this.notificationDispatchService.enqueue(userId, "SUPPORT", "Ticket support cree",
				"Votre demande " + created.ticketNumber() + " a bien ete recue.",
				created.bookingId(), created.paymentTransactionId(), created.tripId(),
				"{\"ticketId\":\"" + created.id() + "\",\"ticketNumber\":\"" + created.ticketNumber() + "\"}");
		return SupportTicketResponse.from(created);
	}

	@Transactional(readOnly = true)
	public SupportTicketPageResponse listForUser(UUID userId, String status, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.max(1, Math.min(size, 100));
		List<SupportTicketRecord> rows = this.supportTicketRepository.listForUser(userId, status, safeSize + 1,
				safePage * safeSize);
		boolean hasMore = rows.size() > safeSize;
		return new SupportTicketPageResponse(
				rows.stream().limit(safeSize).map(SupportTicketResponse::from).toList(),
				safePage, safeSize, hasMore);
	}

	@Transactional(readOnly = true)
	public SupportTicketResponse getForUser(UUID userId, UUID ticketId) {
		return this.supportTicketRepository.findByIdForUser(ticketId, userId)
				.map(SupportTicketResponse::from)
				.orElseThrow(() -> new SupportException(SupportErrorCode.SUPPORT_TICKET_NOT_FOUND));
	}

	@Transactional(readOnly = true)
	public SupportTicketPageResponse listForAdmin(AdminAccess adminAccess, String category, String priority,
			String status, int page, int size) {
		requireRead(adminAccess);
		int safePage = Math.max(page, 0);
		int safeSize = Math.max(1, Math.min(size, 100));
		List<SupportTicketRecord> rows = this.supportTicketRepository.listForAdmin(category, priority, status,
				safeSize + 1, safePage * safeSize);
		boolean hasMore = rows.size() > safeSize;
		return new SupportTicketPageResponse(
				rows.stream().limit(safeSize).map(SupportTicketResponse::from).toList(),
				safePage, safeSize, hasMore);
	}

	@Transactional(readOnly = true)
	public SupportTicketDetailResponse getForAdmin(AdminAccess adminAccess, UUID ticketId) {
		requireRead(adminAccess);
		SupportTicketRecord ticket = this.supportTicketRepository.findById(ticketId)
				.orElseThrow(() -> new SupportException(SupportErrorCode.SUPPORT_TICKET_NOT_FOUND));
		return SupportTicketDetailResponse.from(ticket, this.supportTicketRepository.listMessages(ticketId));
	}

	@Transactional
	public SupportTicketDetailResponse updateForAdmin(AdminAccess adminAccess, UUID ticketId,
			AdminSupportTicketUpdateRequest request) {
		requireWrite(adminAccess);
		SupportTicketRecord ticket = this.supportTicketRepository.findById(ticketId)
				.orElseThrow(() -> new SupportException(SupportErrorCode.SUPPORT_TICKET_NOT_FOUND));
		SupportTicketRecord updated = ticket;
		if (request != null && request.status() != null && !request.status().isBlank()) {
			String status = request.status().trim().toUpperCase(Locale.ROOT);
			if (!STATUSES.contains(status)) {
				throw new SupportException(SupportErrorCode.SUPPORT_VALIDATION_FAILED);
			}
			updated = this.supportTicketRepository.updateStatus(ticketId, status, Instant.now(this.clock));
			this.notificationDispatchService.enqueue(ticket.userId(), "SUPPORT", "Mise a jour support",
					"Votre ticket " + ticket.ticketNumber() + " est maintenant " + status + ".",
					ticket.bookingId(), ticket.paymentTransactionId(), ticket.tripId(),
					"{\"ticketId\":\"" + ticket.id() + "\",\"status\":\"" + status + "\"}");
		}
		if (request != null && request.reply() != null && !request.reply().isBlank()) {
			this.supportTicketRepository.insertMessage(ticketId, null, adminAccess.actorProfileId(),
					request.reply().trim());
			this.notificationDispatchService.enqueue(ticket.userId(), "SUPPORT", "Reponse support",
					"Une reponse a ete ajoutee a votre ticket " + ticket.ticketNumber() + ".",
					ticket.bookingId(), ticket.paymentTransactionId(), ticket.tripId(),
					"{\"ticketId\":\"" + ticket.id() + "\"}");
		}
		return SupportTicketDetailResponse.from(updated, this.supportTicketRepository.listMessages(ticketId));
	}

	@Transactional(readOnly = true)
	public ModerationQueuePageResponse moderationQueue(AdminAccess adminAccess, String type, int page, int size) {
		requireRead(adminAccess);
		int safePage = Math.max(page, 0);
		int safeSize = Math.max(1, Math.min(size, 100));
		String filter = type == null || type.isBlank() ? null : type.trim().toUpperCase(Locale.ROOT);

		List<ModerationQueueItem> items = new ArrayList<>();
		if (filter == null || "RATING".equals(filter)) {
			this.ratingRepository.findFlagged(safeSize + 1, 0).forEach(rating -> items.add(new ModerationQueueItem(
					"RATING", rating.id(), "MEDIUM", "FLAGGED",
					rating.comment() == null ? null
							: rating.comment().substring(0, Math.min(120, rating.comment().length())),
					rating.createdAt())));
		}
		if (filter == null || "TICKET".equals(filter) || "SUPPORT_TICKET".equals(filter)) {
			this.supportTicketRepository.listOpenForModeration(safeSize + 1, 0).forEach(ticket -> items
					.add(new ModerationQueueItem("SUPPORT_TICKET", ticket.id(), ticket.priority(), ticket.status(),
							ticket.subject(), ticket.createdAt())));
		}

		items.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
		int from = Math.min(safePage * safeSize, items.size());
		int to = Math.min(from + safeSize, items.size());
		List<ModerationQueueItem> pageItems = items.subList(from, to);
		boolean hasMore = items.size() > to;
		return new ModerationQueuePageResponse(pageItems, safePage, safeSize, hasMore);
	}

	@Transactional
	public ModerationActionResponse applyModerationAction(AdminAccess adminAccess, ModerationActionRequest request) {
		requireWrite(adminAccess);
		if (request == null || request.targetType() == null || request.targetId() == null || request.action() == null) {
			throw new SupportException(SupportErrorCode.MODERATION_VALIDATION_FAILED);
		}
		String targetType = request.targetType().trim().toUpperCase(Locale.ROOT);
		String action = request.action().trim().toUpperCase(Locale.ROOT);
		if (!TARGET_TYPES.contains(targetType) || !ACTIONS.contains(action)) {
			throw new SupportException(SupportErrorCode.MODERATION_VALIDATION_FAILED);
		}

		if ("RATING".equals(targetType)) {
			RatingRecord rating = this.ratingRepository.findById(request.targetId())
					.orElseThrow(() -> new SupportException(SupportErrorCode.MODERATION_TARGET_NOT_FOUND));
			if ("HIDE".equals(action)) {
				this.ratingRepository.updateStatus(rating.id(), RatingStatus.HIDDEN);
				this.ratingRepository.recalculateAverageForProfile(rating.ratedProfileId());
			}
			else if ("RESTORE".equals(action)) {
				this.ratingRepository.updateStatus(rating.id(), RatingStatus.PUBLISHED);
				this.ratingRepository.recalculateAverageForProfile(rating.ratedProfileId());
			}
		}
		else if ("SUPPORT_TICKET".equals(targetType)) {
			SupportTicketRecord ticket = this.supportTicketRepository.findById(request.targetId())
					.orElseThrow(() -> new SupportException(SupportErrorCode.MODERATION_TARGET_NOT_FOUND));
			if ("CLOSE".equals(action)) {
				this.supportTicketRepository.updateStatus(ticket.id(), "CLOSED", Instant.now(this.clock));
			}
			else if ("ESCALATE".equals(action)) {
				this.supportTicketRepository.updateStatus(ticket.id(), "IN_PROGRESS", null);
			}
		}

		ModerationActionRecord recorded = this.supportTicketRepository.insertModerationAction(
				adminAccess.actorProfileId(), targetType, request.targetId(), action,
				request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
		return ModerationActionResponse.from(recorded);
	}

	private static void validateCreate(SupportTicketCreateRequest request) {
		if (request == null || request.category() == null || request.priority() == null || request.subject() == null
				|| request.message() == null) {
			throw new SupportException(SupportErrorCode.SUPPORT_VALIDATION_FAILED);
		}
		String category = request.category().trim().toUpperCase(Locale.ROOT);
		String priority = request.priority().trim().toUpperCase(Locale.ROOT);
		if (!CATEGORIES.contains(category) || !PRIORITIES.contains(priority) || request.subject().isBlank()
				|| request.message().isBlank()) {
			throw new SupportException(SupportErrorCode.SUPPORT_VALIDATION_FAILED);
		}
	}

	private static void requireRead(AdminAccess adminAccess) {
		if (adminAccess == null || adminAccess.actorProfileId() == null
				|| !(adminAccess.permissions().contains(MODERATION_READ)
						|| adminAccess.permissions().contains(MODERATION_WRITE))) {
			throw new SupportException(SupportErrorCode.MODERATION_ACCESS_DENIED);
		}
	}

	private static void requireWrite(AdminAccess adminAccess) {
		if (adminAccess == null || adminAccess.actorProfileId() == null
				|| !adminAccess.permissions().contains(MODERATION_WRITE)) {
			throw new SupportException(SupportErrorCode.MODERATION_ACCESS_DENIED);
		}
	}

	public record AdminAccess(
			UUID actorProfileId,
			String actorRole,
			List<String> permissions,
			String correlationId) {

		public AdminAccess {
			permissions = permissions == null ? List.of() : List.copyOf(permissions);
			actorRole = actorRole == null || actorRole.isBlank() ? "UNKNOWN" : actorRole;
		}
	}
}
