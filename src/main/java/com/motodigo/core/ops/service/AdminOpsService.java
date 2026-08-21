package com.motodigo.core.ops.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.auth.entity.UserStatus;
import com.motodigo.core.booking.entity.BookingStatus;
import com.motodigo.core.intercity.entity.AnnouncementStatus;
import com.motodigo.core.ops.dto.OpsAnnouncementPageResponse;
import com.motodigo.core.ops.dto.OpsAnnouncementSummaryResponse;
import com.motodigo.core.ops.dto.OpsBookingPageResponse;
import com.motodigo.core.ops.dto.OpsBookingSummaryResponse;
import com.motodigo.core.ops.dto.OpsOverviewResponse;
import com.motodigo.core.ops.dto.OpsPaymentPageResponse;
import com.motodigo.core.ops.dto.OpsPaymentSummaryResponse;
import com.motodigo.core.ops.dto.OpsProfileListItemResponse;
import com.motodigo.core.ops.dto.OpsProfilePageResponse;
import com.motodigo.core.ops.dto.OpsUserDetailResponse;
import com.motodigo.core.ops.dto.OpsUserPageResponse;
import com.motodigo.core.ops.dto.OpsUserSummaryResponse;
import com.motodigo.core.ops.exception.OpsErrorCode;
import com.motodigo.core.ops.exception.OpsException;
import com.motodigo.core.ops.repository.AdminOpsRepository;
import com.motodigo.core.payment.entity.PaymentStatus;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AdminOpsService {

	public static final String OPERATIONS_READ_PERMISSION = "OPERATIONS_READ";
	public static final String FULL_ADMIN_ROLE = "PLATFORM_ADMIN";
	public static final String RECONCILIATION_ENDPOINT = "/api/v1/admin/payments/transactions";
	private static final ZoneId OPS_ZONE = ZoneId.of("Africa/Douala");

	private final AdminOpsRepository adminOpsRepository;

	public AdminOpsService(AdminOpsRepository adminOpsRepository) {
		this.adminOpsRepository = adminOpsRepository;
	}

	@Transactional(readOnly = true)
	public OpsOverviewResponse overview(AdminAccess adminAccess) {
		requireOperationsRead(adminAccess);
		LocalDate today = LocalDate.now(OPS_ZONE);
		Instant dayStart = today.atStartOfDay(OPS_ZONE).toInstant();
		Instant dayEnd = today.plusDays(1).atStartOfDay(OPS_ZONE).toInstant();
		return new OpsOverviewResponse(
				this.adminOpsRepository.countUsers(),
				this.adminOpsRepository.countUsersByStatus(),
				this.adminOpsRepository.countActiveAnnouncements(),
				this.adminOpsRepository.countAnnouncementsByStatus(),
				this.adminOpsRepository.countBookingsByStatus(),
				this.adminOpsRepository.countPaymentsPendingToday(dayStart, dayEnd),
				this.adminOpsRepository.countPaymentsConfirmedToday(dayStart, dayEnd),
				"Reconciliation detaillee aussi disponible via " + RECONCILIATION_ENDPOINT);
	}

	@Transactional(readOnly = true)
	public OpsUserPageResponse listUsers(AdminAccess adminAccess, String status, String search, int page, int size) {
		requireOperationsRead(adminAccess);
		String normalizedStatus = normalizeUserStatus(status);
		boolean revealSensitive = canRevealSensitive(adminAccess);
		List<OpsUserSummaryResponse> rows = this.adminOpsRepository.findUsers(
				normalizedStatus, blankToNull(search), size + 1, page * size);
		boolean hasMore = rows.size() > size;
		List<OpsUserSummaryResponse> pageRows = rows.stream()
				.limit(size)
				.map(user -> new OpsUserSummaryResponse(
						user.id(),
						maskPhone(user.phoneNumber(), revealSensitive),
						user.fullName(),
						maskEmail(user.email(), revealSensitive),
						user.status(),
						user.createdAt(),
						user.profilesCount()))
				.toList();
		return new OpsUserPageResponse(pageRows, page, size, hasMore);
	}

	@Transactional(readOnly = true)
	public OpsUserDetailResponse getUser(AdminAccess adminAccess, UUID userId) {
		requireOperationsRead(adminAccess);
		OpsUserDetailResponse detail = this.adminOpsRepository.findUserDetail(userId)
				.orElseThrow(() -> new OpsException(OpsErrorCode.OPS_USER_NOT_FOUND));
		boolean revealSensitive = canRevealSensitive(adminAccess);
		return new OpsUserDetailResponse(
				detail.id(),
				maskPhone(detail.phoneNumber(), revealSensitive),
				detail.fullName(),
				maskEmail(detail.email(), revealSensitive),
				detail.locale(),
				detail.status(),
				detail.phoneVerifiedAt(),
				detail.createdAt(),
				detail.updatedAt(),
				detail.profiles());
	}

	@Transactional(readOnly = true)
	public OpsProfilePageResponse listProfiles(AdminAccess adminAccess, String validationStatus, String type,
			String search, int page, int size) {
		requireOperationsRead(adminAccess);
		String normalizedStatus = normalizeValidationStatus(validationStatus);
		String normalizedType = normalizeProfileType(type);
		boolean revealSensitive = canRevealSensitive(adminAccess);
		List<OpsProfileListItemResponse> rows = this.adminOpsRepository.findProfiles(
				normalizedStatus, normalizedType, blankToNull(search), size + 1, page * size);
		boolean hasMore = rows.size() > size;
		List<OpsProfileListItemResponse> pageRows = rows.stream()
				.limit(size)
				.map(profile -> new OpsProfileListItemResponse(
						profile.id(),
						profile.userId(),
						profile.type(),
						profile.validationStatus(),
						profile.displayName(),
						profile.active(),
						profile.createdAt(),
						maskPhone(profile.userPhone(), revealSensitive),
						profile.userStatus()))
				.toList();
		return new OpsProfilePageResponse(pageRows, page, size, hasMore);
	}

	@Transactional(readOnly = true)
	public OpsAnnouncementPageResponse listAnnouncements(AdminAccess adminAccess, String status, String search,
			int page, int size) {
		requireOperationsRead(adminAccess);
		String normalizedStatus = normalizeAnnouncementStatus(status);
		List<OpsAnnouncementSummaryResponse> rows = this.adminOpsRepository.findAnnouncements(
				normalizedStatus, blankToNull(search), size + 1, page * size);
		boolean hasMore = rows.size() > size;
		return new OpsAnnouncementPageResponse(rows.stream().limit(size).toList(), page, size, hasMore);
	}

	@Transactional(readOnly = true)
	public OpsBookingPageResponse listBookings(AdminAccess adminAccess, String status, String search, int page,
			int size) {
		requireOperationsRead(adminAccess);
		String normalizedStatus = normalizeBookingStatus(status);
		boolean revealSensitive = canRevealSensitive(adminAccess);
		List<OpsBookingSummaryResponse> rows = this.adminOpsRepository.findBookings(
				normalizedStatus, blankToNull(search), size + 1, page * size);
		boolean hasMore = rows.size() > size;
		List<OpsBookingSummaryResponse> pageRows = rows.stream()
				.limit(size)
				.map(booking -> new OpsBookingSummaryResponse(
						booking.id(),
						booking.announcementId(),
						booking.passengerProfileId(),
						booking.status(),
						booking.seatsRequested(),
						maskPhone(booking.contactPhone(), revealSensitive),
						booking.requestedAt(),
						booking.confirmedAt(),
						booking.cancelledAt(),
						booking.expiresAt()))
				.toList();
		return new OpsBookingPageResponse(pageRows, page, size, hasMore);
	}

	@Transactional(readOnly = true)
	public OpsPaymentPageResponse listPayments(AdminAccess adminAccess, String status, String search, int page,
			int size) {
		requireOperationsRead(adminAccess);
		String normalizedStatus = normalizePaymentStatus(status);
		boolean revealSensitive = canRevealSensitive(adminAccess);
		List<OpsPaymentSummaryResponse> rows = this.adminOpsRepository.findPayments(
				normalizedStatus, blankToNull(search), size + 1, page * size);
		boolean hasMore = rows.size() > size;
		List<OpsPaymentSummaryResponse> pageRows = rows.stream()
				.limit(size)
				.map(payment -> new OpsPaymentSummaryResponse(
						payment.id(),
						payment.type(),
						payment.status(),
						payment.amount(),
						payment.currency(),
						payment.provider(),
						maskPhone(payment.payerPhone(), revealSensitive),
						payment.externalReference(),
						payment.initiatedAt(),
						payment.confirmedAt()))
				.toList();
		return new OpsPaymentPageResponse(pageRows, page, size, hasMore, RECONCILIATION_ENDPOINT);
	}

	public String usersCsv(AdminAccess adminAccess, String status, String search) {
		OpsUserPageResponse page = listUsers(adminAccess, status, search, 0, 1000);
		StringBuilder csv = new StringBuilder("id,phoneNumber,fullName,email,status,createdAt,profilesCount\n");
		for (OpsUserSummaryResponse user : page.users()) {
			csv.append(csvCell(user.id()))
					.append(',').append(csvCell(user.phoneNumber()))
					.append(',').append(csvCell(user.fullName()))
					.append(',').append(csvCell(user.email()))
					.append(',').append(csvCell(user.status()))
					.append(',').append(csvCell(user.createdAt()))
					.append(',').append(user.profilesCount())
					.append('\n');
		}
		return csv.toString();
	}

	public String profilesCsv(AdminAccess adminAccess, String validationStatus, String type, String search) {
		OpsProfilePageResponse page = listProfiles(adminAccess, validationStatus, type, search, 0, 1000);
		StringBuilder csv = new StringBuilder(
				"id,userId,type,validationStatus,displayName,active,createdAt,userPhone,userStatus\n");
		for (OpsProfileListItemResponse row : page.profiles()) {
			csv.append(csvCell(row.id()))
					.append(',').append(csvCell(row.userId()))
					.append(',').append(csvCell(row.type()))
					.append(',').append(csvCell(row.validationStatus()))
					.append(',').append(csvCell(row.displayName()))
					.append(',').append(row.active())
					.append(',').append(csvCell(row.createdAt()))
					.append(',').append(csvCell(row.userPhone()))
					.append(',').append(csvCell(row.userStatus()))
					.append('\n');
		}
		return csv.toString();
	}

	public String announcementsCsv(AdminAccess adminAccess, String status, String search) {
		OpsAnnouncementPageResponse page = listAnnouncements(adminAccess, status, search, 0, 1000);
		StringBuilder csv = new StringBuilder(
				"id,publisherProfileId,departureCity,destinationCity,departureDate,departureTime,priceFcfa,totalSeats,remainingSeats,status,createdAt\n");
		for (OpsAnnouncementSummaryResponse row : page.announcements()) {
			csv.append(csvCell(row.id()))
					.append(',').append(csvCell(row.publisherProfileId()))
					.append(',').append(csvCell(row.departureCity()))
					.append(',').append(csvCell(row.destinationCity()))
					.append(',').append(csvCell(row.departureDate()))
					.append(',').append(csvCell(row.departureTime()))
					.append(',').append(csvCell(row.priceFcfa()))
					.append(',').append(row.totalSeats())
					.append(',').append(row.remainingSeats())
					.append(',').append(csvCell(row.status()))
					.append(',').append(csvCell(row.createdAt()))
					.append('\n');
		}
		return csv.toString();
	}

	public String bookingsCsv(AdminAccess adminAccess, String status, String search) {
		OpsBookingPageResponse page = listBookings(adminAccess, status, search, 0, 1000);
		StringBuilder csv = new StringBuilder(
				"id,announcementId,passengerProfileId,status,seatsRequested,contactPhone,requestedAt,confirmedAt,cancelledAt,expiresAt\n");
		for (OpsBookingSummaryResponse row : page.bookings()) {
			csv.append(csvCell(row.id()))
					.append(',').append(csvCell(row.announcementId()))
					.append(',').append(csvCell(row.passengerProfileId()))
					.append(',').append(csvCell(row.status()))
					.append(',').append(row.seatsRequested())
					.append(',').append(csvCell(row.contactPhone()))
					.append(',').append(csvCell(row.requestedAt()))
					.append(',').append(csvCell(row.confirmedAt()))
					.append(',').append(csvCell(row.cancelledAt()))
					.append(',').append(csvCell(row.expiresAt()))
					.append('\n');
		}
		return csv.toString();
	}

	public String paymentsCsv(AdminAccess adminAccess, String status, String search) {
		OpsPaymentPageResponse page = listPayments(adminAccess, status, search, 0, 1000);
		StringBuilder csv = new StringBuilder(
				"id,type,status,amount,currency,provider,payerPhone,externalReference,initiatedAt,confirmedAt\n");
		for (OpsPaymentSummaryResponse row : page.payments()) {
			csv.append(csvCell(row.id()))
					.append(',').append(csvCell(row.type()))
					.append(',').append(csvCell(row.status()))
					.append(',').append(csvCell(row.amount()))
					.append(',').append(csvCell(row.currency()))
					.append(',').append(csvCell(row.provider()))
					.append(',').append(csvCell(row.payerPhone()))
					.append(',').append(csvCell(row.externalReference()))
					.append(',').append(csvCell(row.initiatedAt()))
					.append(',').append(csvCell(row.confirmedAt()))
					.append('\n');
		}
		return csv.toString();
	}

	private boolean canRevealSensitive(AdminAccess adminAccess) {
		return this.adminOpsRepository.findAdminRole(adminAccess.actorProfileId())
				.map(FULL_ADMIN_ROLE::equals)
				.orElse(false);
	}

	private static void requireOperationsRead(AdminAccess adminAccess) {
		if (adminAccess == null || adminAccess.actorProfileId() == null
				|| !adminAccess.permissions().contains(OPERATIONS_READ_PERMISSION)) {
			throw new OpsException(OpsErrorCode.OPS_ACCESS_DENIED);
		}
	}

	private static String normalizeUserStatus(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UserStatus.valueOf(value.trim().toUpperCase()).name();
		}
		catch (IllegalArgumentException ex) {
			throw new OpsException(OpsErrorCode.OPS_VALIDATION_FAILED);
		}
	}

	private static String normalizeValidationStatus(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return ValidationStatus.valueOf(value.trim().toUpperCase()).name();
		}
		catch (IllegalArgumentException ex) {
			throw new OpsException(OpsErrorCode.OPS_VALIDATION_FAILED);
		}
	}

	private static String normalizeProfileType(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return ProfileType.valueOf(value.trim().toUpperCase()).name();
		}
		catch (IllegalArgumentException ex) {
			throw new OpsException(OpsErrorCode.OPS_VALIDATION_FAILED);
		}
	}

	private static String normalizeAnnouncementStatus(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return AnnouncementStatus.valueOf(value.trim().toUpperCase()).name();
		}
		catch (IllegalArgumentException ex) {
			throw new OpsException(OpsErrorCode.OPS_VALIDATION_FAILED);
		}
	}

	private static String normalizeBookingStatus(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return BookingStatus.valueOf(value.trim().toUpperCase()).name();
		}
		catch (IllegalArgumentException ex) {
			throw new OpsException(OpsErrorCode.OPS_VALIDATION_FAILED);
		}
	}

	private static String normalizePaymentStatus(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return PaymentStatus.valueOf(value.trim().toUpperCase()).name();
		}
		catch (IllegalArgumentException ex) {
			throw new OpsException(OpsErrorCode.OPS_VALIDATION_FAILED);
		}
	}

	public static String maskPhone(String phone, boolean reveal) {
		if (reveal || phone == null || phone.isBlank()) {
			return phone;
		}
		String trimmed = phone.trim();
		if (trimmed.length() < 8) {
			return "****";
		}
		return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
	}

	public static String maskEmail(String email, boolean reveal) {
		if (reveal || email == null || email.isBlank()) {
			return email;
		}
		String trimmed = email.trim();
		int at = trimmed.indexOf('@');
		if (at <= 1) {
			return "****";
		}
		return trimmed.charAt(0) + "****" + trimmed.substring(at);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String csvCell(Object value) {
		if (value == null) {
			return "";
		}
		String text = String.valueOf(value);
		if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
			return "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
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
