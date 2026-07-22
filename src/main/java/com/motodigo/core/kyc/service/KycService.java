package com.motodigo.core.kyc.service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.motodigo.core.kyc.dto.AdminKycDocumentResponse;
import com.motodigo.core.kyc.dto.DocumentRequirementResponse;
import com.motodigo.core.kyc.dto.DownloadedKycDocument;
import com.motodigo.core.kyc.dto.KycDocumentsResponse;
import com.motodigo.core.kyc.dto.OfficialDocumentResponse;
import com.motodigo.core.kyc.dto.UploadKycDocumentResponse;
import com.motodigo.core.kyc.entity.AccessAction;
import com.motodigo.core.kyc.entity.DocumentStatus;
import com.motodigo.core.kyc.entity.DocumentType;
import com.motodigo.core.kyc.entity.KycProfileRecord;
import com.motodigo.core.kyc.entity.OfficialDocumentRecord;
import com.motodigo.core.kyc.exception.KycErrorCode;
import com.motodigo.core.kyc.exception.KycException;
import com.motodigo.core.kyc.repository.KycRepository;
import com.motodigo.core.kyc.service.FileValidationService.ValidatedFile;
import com.motodigo.core.kyc.storage.DocumentStorage;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.repository.ProfileRepository;
import com.motodigo.core.profile.service.PermissionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class KycService {

	private static final Set<DocumentType> DRIVER_REQUIREMENTS = EnumSet.of(
			DocumentType.CNI,
			DocumentType.DRIVER_LICENSE,
			DocumentType.VEHICLE_REGISTRATION,
			DocumentType.INSURANCE,
			DocumentType.PROFILE_PHOTO);
	private static final Set<DocumentStatus> COMPLETE_STATUSES = EnumSet.of(
			DocumentStatus.PENDING_REVIEW,
			DocumentStatus.ACCEPTED);

	private final ProfileRepository profileRepository;
	private final PermissionService permissionService;
	private final KycRepository kycRepository;
	private final FileValidationService fileValidationService;
	private final KycCryptoService cryptoService;
	private final DocumentStorage documentStorage;

	public KycService(ProfileRepository profileRepository, PermissionService permissionService, KycRepository kycRepository,
			FileValidationService fileValidationService, KycCryptoService cryptoService, DocumentStorage documentStorage) {
		this.profileRepository = profileRepository;
		this.permissionService = permissionService;
		this.kycRepository = kycRepository;
		this.fileValidationService = fileValidationService;
		this.cryptoService = cryptoService;
		this.documentStorage = documentStorage;
	}

	@Transactional(readOnly = true)
	public KycDocumentsResponse listCurrentUserDocuments(UUID userId) {
		ProfileRecord profile = eligibleActiveProfile(userId);
		List<OfficialDocumentRecord> documents = this.kycRepository.findDocumentsByProfile(profile.id());
		return new KycDocumentsResponse(profile.id(), profile.type(), profile.validationStatus(),
				requirements(profile.type(), documents), documents.stream().map(OfficialDocumentResponse::from).toList());
	}

	@Transactional
	public UploadKycDocumentResponse uploadDocument(UUID userId, DocumentType type, UUID vehicleId, MultipartFile file) {
		ProfileRecord profile = eligibleActiveProfile(userId);
		requireDocumentType(profile.type(), type);
		if (vehicleId != null && !this.kycRepository.vehicleBelongsToProfile(profile.id(), vehicleId)) {
			throw new KycException(KycErrorCode.KYC_ACCESS_DENIED);
		}

		ValidatedFile validatedFile = this.fileValidationService.validate(file);
		String checksum = this.cryptoService.checksum(validatedFile.bytes());
		byte[] encrypted = this.cryptoService.encrypt(validatedFile.bytes());
		String storageKey = this.documentStorage.put(objectKey(profile.id()), encrypted, validatedFile.mimeType());

		OfficialDocumentRecord saved;
		try {
			saved = this.kycRepository.saveDocument(profile.id(), vehicleId, type, storageKey, validatedFile.mimeType(),
					validatedFile.bytes().length, checksum);
		}
		catch (DuplicateKeyException ex) {
			throw new KycException(KycErrorCode.DOCUMENT_DUPLICATE, ex);
		}

		List<OfficialDocumentRecord> documents = this.kycRepository.findDocumentsByProfile(profile.id());
		if (isComplete(profile.type(), documents)) {
			this.kycRepository.markProfilePending(profile.id());
		}
		return new UploadKycDocumentResponse(profile.id(), profile.validationStatus(), OfficialDocumentResponse.from(saved),
				requirements(profile.type(), documents));
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
				documents.stream().map(OfficialDocumentResponse::from).toList());
	}

	@Transactional
	public DownloadedKycDocument downloadDocumentForAdmin(UUID documentId, AdminAccess adminAccess) {
		requireAdminReview(adminAccess);
		OfficialDocumentRecord document = this.kycRepository.findDocumentById(documentId)
				.orElseThrow(() -> new KycException(KycErrorCode.DOCUMENT_NOT_FOUND));
		KycProfileRecord profile = this.kycRepository.findProfileById(document.profileId())
				.orElseThrow(() -> new KycException(KycErrorCode.KYC_PROFILE_NOT_ELIGIBLE));
		requireEligibleProfile(profile.type());
		this.kycRepository.saveAudit(document.id(), adminAccess.actorProfileId(), AccessAction.DOWNLOAD,
				adminAccess.actorRole(), adminAccess.correlationId());
		byte[] encrypted = this.documentStorage.get(document.encryptedStorageKey());
		return new DownloadedKycDocument(document, this.cryptoService.decrypt(encrypted));
	}

	private ProfileRecord eligibleActiveProfile(UUID userId) {
		ProfileRecord profile = this.profileRepository.findActiveByUserId(userId)
				.orElseThrow(() -> new KycException(KycErrorCode.KYC_PROFILE_NOT_ELIGIBLE));
		requireEligibleProfile(profile.type());
		if (!this.permissionService.allowedActions(profile).contains("KYC_UPLOAD")) {
			throw new KycException(KycErrorCode.KYC_ACCESS_DENIED);
		}
		return profile;
	}

	private static void requireEligibleProfile(ProfileType profileType) {
		if (profileType != ProfileType.MOTOTAXI && profileType != ProfileType.TRANSPORTER) {
			throw new KycException(KycErrorCode.KYC_PROFILE_NOT_ELIGIBLE);
		}
	}

	private static void requireDocumentType(ProfileType profileType, DocumentType documentType) {
		if (!requiredTypes(profileType).contains(documentType)) {
			throw new KycException(KycErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
		}
	}

	private static void requireAdminReview(AdminAccess adminAccess) {
		if (adminAccess == null || adminAccess.actorProfileId() == null
				|| !adminAccess.permissions().contains("KYC_REVIEW_READ")) {
			throw new KycException(KycErrorCode.KYC_ACCESS_DENIED);
		}
	}

	private static List<DocumentRequirementResponse> requirements(ProfileType profileType, List<OfficialDocumentRecord> documents) {
		Map<DocumentType, OfficialDocumentRecord> latestByType = new EnumMap<>(DocumentType.class);
		documents.stream()
				.sorted(Comparator.comparing(OfficialDocumentRecord::uploadedAt).reversed())
				.forEach(document -> latestByType.putIfAbsent(document.type(), document));
		return requiredTypes(profileType).stream()
				.sorted(Comparator.comparing(Enum::name))
				.map(type -> {
					OfficialDocumentRecord document = latestByType.get(type);
					return new DocumentRequirementResponse(type, true, document != null,
							document == null ? null : document.status());
				})
				.toList();
	}

	private static boolean isComplete(ProfileType profileType, List<OfficialDocumentRecord> documents) {
		Map<DocumentType, DocumentStatus> latestStatusByType = new EnumMap<>(DocumentType.class);
		documents.stream()
				.sorted(Comparator.comparing(OfficialDocumentRecord::uploadedAt).reversed())
				.forEach(document -> latestStatusByType.putIfAbsent(document.type(), document.status()));
		return requiredTypes(profileType).stream()
				.allMatch(type -> COMPLETE_STATUSES.contains(latestStatusByType.get(type)));
	}

	private static Set<DocumentType> requiredTypes(ProfileType profileType) {
		requireEligibleProfile(profileType);
		return DRIVER_REQUIREMENTS;
	}

	private static String objectKey(UUID profileId) {
		return "kyc/" + profileId + "/" + UUID.randomUUID() + ".enc";
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
