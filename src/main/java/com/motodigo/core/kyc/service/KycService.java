package com.motodigo.core.kyc.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.motodigo.core.kyc.dto.AdminKycDecisionRequest;
import com.motodigo.core.kyc.dto.AdminKycDecisionResponse;
import com.motodigo.core.kyc.dto.AdminKycDocumentDecisionResponse;
import com.motodigo.core.kyc.dto.AdminKycDocumentResponse;
import com.motodigo.core.kyc.dto.AdminKycQueueItemResponse;
import com.motodigo.core.kyc.dto.DocumentRequirementResponse;
import com.motodigo.core.kyc.dto.DocumentDecisionResponse;
import com.motodigo.core.kyc.dto.DownloadedKycDocument;
import com.motodigo.core.kyc.dto.KycDocumentsResponse;
import com.motodigo.core.kyc.dto.OfficialDocumentResponse;
import com.motodigo.core.kyc.dto.UploadKycDocumentResponse;
import com.motodigo.core.kyc.dto.ValidationDecisionResponse;
import com.motodigo.core.kyc.entity.AccessAction;
import com.motodigo.core.kyc.entity.DocumentStatus;
import com.motodigo.core.kyc.entity.DocumentType;
import com.motodigo.core.kyc.entity.KycDecision;
import com.motodigo.core.kyc.entity.KycProfileRecord;
import com.motodigo.core.kyc.entity.OfficialDocumentRecord;
import com.motodigo.core.kyc.entity.ValidationDecisionRecord;
import com.motodigo.core.kyc.exception.KycErrorCode;
import com.motodigo.core.kyc.exception.KycException;
import com.motodigo.core.kyc.repository.KycRepository;
import com.motodigo.core.kyc.storage.DocumentStorage;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class KycService {

	private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
	private static final List<DocumentType> PASSENGER_REQUIRED_DOCUMENTS = List.of(DocumentType.CNI);
	private static final List<DocumentType> SELF_SERVICE_REQUIRED_DOCUMENTS = List.of(
			DocumentType.CNI,
			DocumentType.PROFILE_PHOTO,
			DocumentType.DRIVER_LICENSE,
			DocumentType.VEHICLE_REGISTRATION,
			DocumentType.INSURANCE);
	private static final List<DocumentType> TRANSPORTER_REQUIRED_DOCUMENTS = List.of(
			DocumentType.CNI,
			DocumentType.PROFILE_PHOTO,
			DocumentType.DRIVER_LICENSE,
			DocumentType.VEHICLE_REGISTRATION,
			DocumentType.INSURANCE,
			DocumentType.VEHICLE_PHOTO);
	private static final List<String> ALLOWED_MIME_TYPES = List.of("image/jpeg", "image/png", "application/pdf");
	private static final List<String> ALLOWED_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".pdf");
	private static final String DEFAULT_TRANSPORTER_VEHICLE_TYPE = "CAR";
	private static final int DEFAULT_TRANSPORTER_VEHICLE_CAPACITY = 4;

	private final KycRepository kycRepository;
	private final DocumentStorage documentStorage;

	public KycService(KycRepository kycRepository, DocumentStorage documentStorage) {
		this.kycRepository = kycRepository;
		this.documentStorage = documentStorage;
	}

	@Transactional(readOnly = true)
	public KycDocumentsResponse listCurrentDocuments(UUID userId, UUID activeProfileId) {
		KycProfileRecord profile = ownedActiveProfile(userId, activeProfileId);
		List<OfficialDocumentRecord> documents = this.kycRepository.findDocumentsByProfile(profile.id());
		return KycDocumentsResponse.from(profile.id(), profile.type(), profile.validationStatus(),
				requirements(profile.type(), documents), documents);
	}

	@Transactional
	public UploadKycDocumentResponse uploadDocument(UUID userId, UUID activeProfileId, DocumentType type, UUID vehicleId,
			MultipartFile file) {
		KycProfileRecord profile = ownedActiveProfile(userId, activeProfileId);
		if (!requiredDocumentTypes(profile.type()).contains(type)) {
			throw new KycException(KycErrorCode.KYC_DOCUMENT_INVALID);
		}
		byte[] content = content(file);
		String mimeType = normalizedMimeType(file);
		String fileName = sanitizedFileName(file.getOriginalFilename(), type);
		validateFile(fileName, mimeType, content.length);
		UUID linkedVehicleId = resolveVehicleId(profile, type, vehicleId);

		String checksum = sha256(content);
		String storageKey = "kyc/" + profile.id() + "/" + UUID.randomUUID() + "-" + fileName + ".enc";
		String encryptedStorageKey = this.documentStorage.put(storageKey, content, mimeType);
		OfficialDocumentRecord document;
		try {
			document = this.kycRepository.saveDocument(profile.id(), linkedVehicleId, type, DocumentStatus.PENDING_REVIEW,
					encryptedStorageKey, fileName, mimeType, content.length, checksum);
		}
		catch (DuplicateKeyException ex) {
			throw new KycException(KycErrorCode.KYC_DOCUMENT_DUPLICATE);
		}

		if (profile.type() == ProfileType.TRANSPORTER && type == DocumentType.VEHICLE_PHOTO && linkedVehicleId != null) {
			this.kycRepository.updateTransporterVehicleId(profile.id(), linkedVehicleId);
		}

		List<OfficialDocumentRecord> documents = this.kycRepository.findDocumentsByProfile(profile.id());
		List<DocumentRequirementResponse> requirements = requirements(profile.type(), documents);
		ValidationStatus validationStatus = profile.validationStatus();
		if (isComplete(requirements) && validationStatus != ValidationStatus.PENDING) {
			validationStatus = ValidationStatus.PENDING;
			this.kycRepository.updateProfileStatus(profile.id(), validationStatus);
		}
		return UploadKycDocumentResponse.from(profile.id(), validationStatus, document, requirements);
	}

	private UUID resolveVehicleId(KycProfileRecord profile, DocumentType type, UUID vehicleId) {
		if (vehicleId != null) {
			if (!this.kycRepository.isVehicleOwnedByProfile(vehicleId, profile.id())) {
				throw new KycException(KycErrorCode.KYC_DOCUMENT_INVALID);
			}
			return vehicleId;
		}
		if (profile.type() != ProfileType.TRANSPORTER || type != DocumentType.VEHICLE_PHOTO) {
			return null;
		}
		UUID existing = this.kycRepository.findTransporterVehicleId(profile.id());
		if (existing != null) {
			return existing;
		}
		UUID created = this.kycRepository.createVehicleForProfile(profile.id(), DEFAULT_TRANSPORTER_VEHICLE_TYPE,
				DEFAULT_TRANSPORTER_VEHICLE_CAPACITY, "PENDING");
		if (created == null) {
			throw new KycException(KycErrorCode.KYC_DOCUMENT_INVALID);
		}
		return created;
	}

	private void syncVehicleStatusFromDocument(OfficialDocumentRecord document, boolean accepted) {
		if (document.type() != DocumentType.VEHICLE_PHOTO || document.vehicleId() == null) {
			return;
		}
		this.kycRepository.updateVehicleValidationStatus(document.vehicleId(), accepted ? "VALIDATED" : "REJECTED");
	}

	@Transactional(readOnly = true)
	public List<AdminKycQueueItemResponse> listPending(AdminAccess adminAccess) {
		return listReviews(adminAccess, null, ValidationStatus.PENDING);
	}

	@Transactional(readOnly = true)
	public List<AdminKycQueueItemResponse> listReviews(AdminAccess adminAccess, ProfileType profileType,
			ValidationStatus status) {
		requireAdminReview(adminAccess);
		if (profileType != null) {
			requireEligibleProfile(profileType);
		}
		return this.kycRepository.findReviewQueue(profileType, status == null ? ValidationStatus.PENDING : status);
	}

	@Transactional
	public AdminKycDocumentResponse listDocumentsForAdmin(UUID profileId, AdminAccess adminAccess) {
		requireAdminReview(adminAccess);
		KycProfileRecord profile = this.kycRepository.findProfileById(profileId)
				.orElseThrow(() -> new KycException(KycErrorCode.KYC_PROFILE_NOT_ELIGIBLE));
		requireEligibleProfile(profile.type());
		List<OfficialDocumentRecord> documents = this.kycRepository.findDocumentsByProfile(profileId);
		for (OfficialDocumentRecord document : documents) {
			this.kycRepository.saveAudit(document.id(), adminAccess.actorProfileId(), AccessAction.VIEW,
					adminAccess.actorRole(), adminAccess.correlationId());
		}
		return new AdminKycDocumentResponse(profile.id(), profile.type(), profile.validationStatus(), profile.displayName(),
				documents.stream().map(OfficialDocumentResponse::from).toList(),
				this.kycRepository.findDecisionsByProfile(profileId).stream()
						.map(ValidationDecisionResponse::from)
				.toList(),
				this.kycRepository.findDocumentDecisionsByProfile(profileId).stream()
						.map(DocumentDecisionResponse::from)
						.toList());
	}

	@Transactional
	public DownloadedKycDocument downloadDocumentForAdmin(UUID documentId, AdminAccess adminAccess) {
		requireAdminReview(adminAccess);
		OfficialDocumentRecord document = this.kycRepository.findDocumentById(documentId)
				.orElseThrow(() -> new KycException(KycErrorCode.KYC_DOCUMENT_NOT_FOUND));
		KycProfileRecord profile = this.kycRepository.findProfileById(document.profileId())
				.orElseThrow(() -> new KycException(KycErrorCode.KYC_PROFILE_NOT_ELIGIBLE));
		requireEligibleProfile(profile.type());
		this.kycRepository.saveAudit(document.id(), adminAccess.actorProfileId(), AccessAction.DOWNLOAD,
				adminAccess.actorRole(), adminAccess.correlationId());
		return new DownloadedKycDocument(document, this.documentStorage.get(document.encryptedStorageKey()));
	}

	@Transactional
	public AdminKycDecisionResponse decide(UUID profileId, AdminKycDecisionRequest request, AdminAccess adminAccess) {
		requireAdminDecision(adminAccess);
		if (request.decision() == KycDecision.REJECT && isBlank(request.reason())) {
			throw new KycException(KycErrorCode.KYC_DECISION_REASON_REQUIRED);
		}

		KycProfileRecord profile = this.kycRepository.lockProfileForDecision(profileId)
				.orElseThrow(() -> new KycException(KycErrorCode.KYC_PROFILE_NOT_ELIGIBLE));
		requireEligibleProfile(profile.type());
		if (profile.validationStatus() != ValidationStatus.PENDING) {
			throw new KycException(KycErrorCode.KYC_DECISION_NOT_ALLOWED);
		}

		List<OfficialDocumentRecord> pendingDocuments = this.kycRepository.findPendingDocumentsByProfile(profileId);
		if (pendingDocuments.isEmpty()) {
			throw new KycException(KycErrorCode.KYC_DECISION_NOT_ALLOWED);
		}

		ValidationStatus profileStatus = request.decision() == KycDecision.VALIDATE
				? ValidationStatus.VALIDATED
				: ValidationStatus.REJECTED;
		DocumentStatus documentStatus = request.decision() == KycDecision.VALIDATE
				? DocumentStatus.ACCEPTED
				: DocumentStatus.REJECTED;
		AccessAction accessAction = request.decision() == KycDecision.VALIDATE ? AccessAction.APPROVE : AccessAction.REJECT;
		String reason = blankToNull(request.reason());

		this.kycRepository.updateProfileStatus(profileId, profileStatus);
		this.kycRepository.updatePendingDocuments(profileId, documentStatus, request.decision() == KycDecision.REJECT ? reason : null);
		ValidationDecisionRecord decisionRecord = this.kycRepository.saveDecision(profileId, adminAccess.actorProfileId(),
				request.decision(), reason);
		for (OfficialDocumentRecord document : pendingDocuments) {
			this.kycRepository.saveAudit(document.id(), adminAccess.actorProfileId(), accessAction,
					adminAccess.actorRole(), adminAccess.correlationId());
			syncVehicleStatusFromDocument(document, request.decision() == KycDecision.VALIDATE);
		}
		this.kycRepository.queueKycDecisionNotification(profile.userId(), request.decision(), profile.displayName(), reason);

		return new AdminKycDecisionResponse(profileId, profileStatus, request.decision(),
				ValidationDecisionResponse.from(decisionRecord));
	}

	@Transactional
	public AdminKycDocumentDecisionResponse decideDocument(UUID documentId, AdminKycDecisionRequest request,
			AdminAccess adminAccess) {
		requireAdminDecision(adminAccess);
		if (request.decision() == KycDecision.REJECT && isBlank(request.reason())) {
			throw new KycException(KycErrorCode.KYC_DECISION_REASON_REQUIRED);
		}

		OfficialDocumentRecord document = this.kycRepository.lockDocumentById(documentId)
				.orElseThrow(() -> new KycException(KycErrorCode.KYC_DOCUMENT_NOT_FOUND));
		KycProfileRecord profile = this.kycRepository.lockProfileForDecision(document.profileId())
				.orElseThrow(() -> new KycException(KycErrorCode.KYC_PROFILE_NOT_ELIGIBLE));
		requireEligibleProfile(profile.type());
		if (profile.validationStatus() != ValidationStatus.PENDING
				|| document.status() != DocumentStatus.PENDING_REVIEW) {
			throw new KycException(KycErrorCode.KYC_DECISION_NOT_ALLOWED);
		}

		String reason = blankToNull(request.reason());
		var decisionRecord = this.kycRepository.saveDocumentDecision(document, adminAccess.actorProfileId(),
				request.decision(), request.decision() == KycDecision.REJECT ? reason : null);
		ValidationStatus profileStatus = profile.validationStatus();

		if (request.decision() == KycDecision.VALIDATE) {
			this.kycRepository.updateDocumentStatus(document.id(), DocumentStatus.ACCEPTED, null);
			this.kycRepository.saveAudit(document.id(), adminAccess.actorProfileId(), AccessAction.APPROVE,
					adminAccess.actorRole(), adminAccess.correlationId());
			syncVehicleStatusFromDocument(document, true);
			if (allRequiredDocumentsAccepted(profile.id(), profile.type())) {
				profileStatus = ValidationStatus.VALIDATED;
				this.kycRepository.updateProfileStatus(profile.id(), profileStatus);
				this.kycRepository.queueKycDecisionNotification(profile.userId(), KycDecision.VALIDATE,
						profile.displayName(), null);
			}
		}
		else {
			this.kycRepository.saveAudit(document.id(), adminAccess.actorProfileId(), AccessAction.REJECT,
					adminAccess.actorRole(), adminAccess.correlationId());
			syncVehicleStatusFromDocument(document, false);
			this.documentStorage.delete(document.encryptedStorageKey());
			this.kycRepository.deleteDocument(document.id());
			profileStatus = this.kycRepository.countPendingDocumentsByProfile(profile.id()) > 0
					? ValidationStatus.PENDING
					: ValidationStatus.REJECTED;
			this.kycRepository.updateProfileStatus(profile.id(), profileStatus);
			this.kycRepository.queueKycDecisionNotification(profile.userId(), KycDecision.REJECT,
					profile.displayName(), reason);
		}

		return new AdminKycDocumentDecisionResponse(profile.id(), document.id(), profileStatus, request.decision(),
				DocumentDecisionResponse.from(decisionRecord));
	}

	private static void requireEligibleProfile(ProfileType profileType) {
		if (profileType != ProfileType.PASSENGER
				&& profileType != ProfileType.MOTOTAXI
				&& profileType != ProfileType.TRANSPORTER) {
			throw new KycException(KycErrorCode.KYC_PROFILE_NOT_ELIGIBLE);
		}
	}

	private static void requireAdminReview(AdminAccess adminAccess) {
		if (adminAccess == null || adminAccess.actorProfileId() == null
				|| !adminAccess.permissions().contains("KYC_REVIEW_READ")) {
			throw new KycException(KycErrorCode.KYC_ACCESS_DENIED);
		}
	}

	private static void requireAdminDecision(AdminAccess adminAccess) {
		if (adminAccess == null || adminAccess.actorProfileId() == null
				|| !adminAccess.permissions().contains("KYC_DECIDE")) {
			throw new KycException(KycErrorCode.KYC_ACCESS_DENIED);
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String blankToNull(String value) {
		return isBlank(value) ? null : value.trim();
	}

	private KycProfileRecord ownedActiveProfile(UUID userId, UUID activeProfileId) {
		if (userId == null || activeProfileId == null) {
			throw new KycException(KycErrorCode.KYC_ACCESS_DENIED);
		}
		KycProfileRecord profile = this.kycRepository.findProfileById(activeProfileId)
				.orElseThrow(() -> new KycException(KycErrorCode.KYC_PROFILE_NOT_ELIGIBLE));
		if (!Objects.equals(profile.userId(), userId)) {
			throw new KycException(KycErrorCode.KYC_ACCESS_DENIED);
		}
		requireEligibleProfile(profile.type());
		if (profile.validationStatus() == ValidationStatus.SUSPENDED) {
			throw new KycException(KycErrorCode.KYC_ACCESS_DENIED);
		}
		return profile;
	}

	private static List<DocumentRequirementResponse> requirements(ProfileType profileType,
			List<OfficialDocumentRecord> documents) {
		Map<DocumentType, OfficialDocumentRecord> latestByType = new EnumMap<>(DocumentType.class);
		for (OfficialDocumentRecord document : documents) {
			latestByType.putIfAbsent(document.type(), document);
		}
		return requiredDocumentTypes(profileType).stream()
				.map(type -> requirement(type, latestByType.get(type)))
				.toList();
	}

	private static DocumentRequirementResponse requirement(DocumentType type, OfficialDocumentRecord latest) {
		DocumentStatus status = latest == null ? null : latest.status();
		boolean uploaded = status == DocumentStatus.PENDING_REVIEW
				|| status == DocumentStatus.ACCEPTED
				|| status == DocumentStatus.UPLOADED
				|| status == DocumentStatus.ENCRYPTED;
		return new DocumentRequirementResponse(type, true, uploaded, status);
	}

	private static List<DocumentType> requiredDocumentTypes(ProfileType profileType) {
		requireEligibleProfile(profileType);
		if (profileType == ProfileType.PASSENGER) {
			return PASSENGER_REQUIRED_DOCUMENTS;
		}
		if (profileType == ProfileType.TRANSPORTER) {
			return TRANSPORTER_REQUIRED_DOCUMENTS;
		}
		return SELF_SERVICE_REQUIRED_DOCUMENTS;
	}

	private static boolean isComplete(List<DocumentRequirementResponse> requirements) {
		return requirements.stream().allMatch(DocumentRequirementResponse::uploaded);
	}

	private boolean allRequiredDocumentsAccepted(UUID profileId, ProfileType profileType) {
		List<DocumentType> requiredTypes = requiredDocumentTypes(profileType);
		return this.kycRepository.countAcceptedRequiredDocumentTypes(profileId, requiredTypes) == requiredTypes.size();
	}

	private static byte[] content(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new KycException(KycErrorCode.KYC_DOCUMENT_INVALID);
		}
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new KycException(KycErrorCode.KYC_DOCUMENT_INVALID);
		}
	}

	private static void validateFile(String fileName, String mimeType, long sizeBytes) {
		if (sizeBytes <= 0 || sizeBytes > MAX_FILE_SIZE_BYTES) {
			throw new KycException(KycErrorCode.KYC_DOCUMENT_INVALID);
		}
		if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
			throw new KycException(KycErrorCode.KYC_DOCUMENT_INVALID);
		}
		String lowerName = fileName.toLowerCase(Locale.ROOT);
		if (ALLOWED_EXTENSIONS.stream().noneMatch(lowerName::endsWith)) {
			throw new KycException(KycErrorCode.KYC_DOCUMENT_INVALID);
		}
	}

	private static String normalizedMimeType(MultipartFile file) {
		String value = file.getContentType();
		if (value == null || value.isBlank()) {
			String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
			if (name.endsWith(".pdf")) return "application/pdf";
			if (name.endsWith(".png")) return "image/png";
			if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
			return "application/octet-stream";
		}
		return value.toLowerCase(Locale.ROOT);
	}

	private static String sanitizedFileName(String originalFileName, DocumentType type) {
		String fallback = type.name().toLowerCase(Locale.ROOT) + ".jpg";
		String source = originalFileName == null || originalFileName.isBlank() ? fallback : originalFileName;
		String onlyName = source.replace("\\", "/");
		int slash = onlyName.lastIndexOf('/');
		if (slash >= 0) {
			onlyName = onlyName.substring(slash + 1);
		}
		String sanitized = onlyName.replaceAll("[^A-Za-z0-9._-]", "-");
		return sanitized.isBlank() ? fallback : sanitized;
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
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
