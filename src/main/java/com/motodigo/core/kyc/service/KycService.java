package com.motodigo.core.kyc.service;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.kyc.dto.AdminKycDecisionRequest;
import com.motodigo.core.kyc.dto.AdminKycDecisionResponse;
import com.motodigo.core.kyc.dto.AdminKycDocumentResponse;
import com.motodigo.core.kyc.dto.AdminKycQueueItemResponse;
import com.motodigo.core.kyc.dto.DownloadedKycDocument;
import com.motodigo.core.kyc.dto.OfficialDocumentResponse;
import com.motodigo.core.kyc.dto.ValidationDecisionResponse;
import com.motodigo.core.kyc.entity.AccessAction;
import com.motodigo.core.kyc.entity.DocumentStatus;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class KycService {

	private final KycRepository kycRepository;
	private final DocumentStorage documentStorage;

	public KycService(KycRepository kycRepository, DocumentStorage documentStorage) {
		this.kycRepository = kycRepository;
		this.documentStorage = documentStorage;
	}

	@Transactional(readOnly = true)
	public List<AdminKycQueueItemResponse> listPending(AdminAccess adminAccess) {
		requireAdminReview(adminAccess);
		return this.kycRepository.findPendingQueue();
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
		}
		this.kycRepository.queueKycDecisionNotification(profile.userId(), request.decision(), profile.displayName(), reason);

		return new AdminKycDecisionResponse(profileId, profileStatus, request.decision(),
				ValidationDecisionResponse.from(decisionRecord));
	}

	private static void requireEligibleProfile(ProfileType profileType) {
		if (profileType != ProfileType.MOTOTAXI && profileType != ProfileType.TRANSPORTER) {
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
