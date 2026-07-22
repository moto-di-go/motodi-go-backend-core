package com.motodigo.core.kyc.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.kyc.dto.AdminKycDecisionRequest;
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
import com.motodigo.core.kyc.service.KycService.AdminAccess;
import com.motodigo.core.kyc.storage.DocumentStorage;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

	@Mock
	private KycRepository kycRepository;
	@Mock
	private DocumentStorage documentStorage;

	private KycService service;

	@BeforeEach
	void setUp() {
		this.service = new KycService(this.kycRepository, this.documentStorage);
	}

	@Test
	void decideRequiresKycDecisionPermission() {
		assertThatThrownBy(() -> this.service.decide(UUID.randomUUID(),
				new AdminKycDecisionRequest(KycDecision.VALIDATE, null), access("KYC_REVIEW_READ")))
				.isInstanceOf(KycException.class)
				.extracting("errorCode")
				.isEqualTo(KycErrorCode.KYC_ACCESS_DENIED);

		verify(this.kycRepository, never()).lockProfileForDecision(any());
	}

	@Test
	void rejectRequiresReason() {
		assertThatThrownBy(() -> this.service.decide(UUID.randomUUID(),
				new AdminKycDecisionRequest(KycDecision.REJECT, " "), access("KYC_DECIDE")))
				.isInstanceOf(KycException.class)
				.extracting("errorCode")
				.isEqualTo(KycErrorCode.KYC_DECISION_REASON_REQUIRED);

		verify(this.kycRepository, never()).lockProfileForDecision(any());
	}

	@Test
	void validateUpdatesProfileDocumentsHistoryAuditAndNotification() {
		UUID profileId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID actorId = UUID.randomUUID();
		OfficialDocumentRecord document = document(profileId);
		ValidationDecisionRecord decision = decision(profileId, actorId, KycDecision.VALIDATE, null);
		when(this.kycRepository.lockProfileForDecision(profileId))
				.thenReturn(Optional.of(profile(profileId, userId, ProfileType.MOTOTAXI, ValidationStatus.PENDING)));
		when(this.kycRepository.findPendingDocumentsByProfile(profileId)).thenReturn(List.of(document));
		when(this.kycRepository.saveDecision(profileId, actorId, KycDecision.VALIDATE, null)).thenReturn(decision);

		this.service.decide(profileId, new AdminKycDecisionRequest(KycDecision.VALIDATE, null),
				new AdminAccess(actorId, "ADMIN", List.of("KYC_DECIDE"), "corr-1"));

		verify(this.kycRepository).updateProfileStatus(profileId, ValidationStatus.VALIDATED);
		verify(this.kycRepository).updatePendingDocuments(profileId, DocumentStatus.ACCEPTED, null);
		verify(this.kycRepository).saveAudit(document.id(), actorId, AccessAction.APPROVE, "ADMIN", "corr-1");
		verify(this.kycRepository).queueKycDecisionNotification(userId, KycDecision.VALIDATE, "Aline", null);
	}

	@Test
	void rejectUpdatesProfileDocumentsHistoryAuditAndNotification() {
		UUID profileId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID actorId = UUID.randomUUID();
		OfficialDocumentRecord document = document(profileId);
		ValidationDecisionRecord decision = decision(profileId, actorId, KycDecision.REJECT, "Photo illisible");
		when(this.kycRepository.lockProfileForDecision(profileId))
				.thenReturn(Optional.of(profile(profileId, userId, ProfileType.TRANSPORTER, ValidationStatus.PENDING)));
		when(this.kycRepository.findPendingDocumentsByProfile(profileId)).thenReturn(List.of(document));
		when(this.kycRepository.saveDecision(profileId, actorId, KycDecision.REJECT, "Photo illisible")).thenReturn(decision);

		this.service.decide(profileId, new AdminKycDecisionRequest(KycDecision.REJECT, " Photo illisible "),
				new AdminAccess(actorId, "ADMIN", List.of("KYC_DECIDE"), "corr-1"));

		verify(this.kycRepository).updateProfileStatus(profileId, ValidationStatus.REJECTED);
		verify(this.kycRepository).updatePendingDocuments(profileId, DocumentStatus.REJECTED, "Photo illisible");
		verify(this.kycRepository).saveAudit(document.id(), actorId, AccessAction.REJECT, "ADMIN", "corr-1");
		verify(this.kycRepository).queueKycDecisionNotification(userId, KycDecision.REJECT, "Aline", "Photo illisible");
	}

	@Test
	void decideRejectsUnsupportedOrNonPendingProfile() {
		UUID profileId = UUID.randomUUID();
		when(this.kycRepository.lockProfileForDecision(profileId))
				.thenReturn(Optional.of(profile(profileId, UUID.randomUUID(), ProfileType.AGENCY, ValidationStatus.PENDING)));

		assertThatThrownBy(() -> this.service.decide(profileId,
				new AdminKycDecisionRequest(KycDecision.VALIDATE, null), access("KYC_DECIDE")))
				.isInstanceOf(KycException.class)
				.extracting("errorCode")
				.isEqualTo(KycErrorCode.KYC_PROFILE_NOT_ELIGIBLE);

		UUID validatedProfileId = UUID.randomUUID();
		when(this.kycRepository.lockProfileForDecision(validatedProfileId))
				.thenReturn(Optional.of(profile(validatedProfileId, UUID.randomUUID(), ProfileType.MOTOTAXI,
						ValidationStatus.VALIDATED)));

		assertThatThrownBy(() -> this.service.decide(validatedProfileId,
				new AdminKycDecisionRequest(KycDecision.VALIDATE, null), access("KYC_DECIDE")))
				.isInstanceOf(KycException.class)
				.extracting("errorCode")
				.isEqualTo(KycErrorCode.KYC_DECISION_NOT_ALLOWED);
	}

	@Test
	void downloadRequiresReviewPermissionAuditsAndReadsStorage() {
		UUID profileId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID actorId = UUID.randomUUID();
		OfficialDocumentRecord document = new OfficialDocumentRecord(documentId, profileId, null, DocumentType.CNI,
				DocumentStatus.PENDING_REVIEW, "s3://motodigo-kyc/kyc/doc.pdf", "application/pdf", 10, null,
				Instant.now(), Instant.now());
		when(this.kycRepository.findDocumentById(documentId)).thenReturn(Optional.of(document));
		when(this.kycRepository.findProfileById(profileId))
				.thenReturn(Optional.of(profile(profileId, UUID.randomUUID(), ProfileType.MOTOTAXI, ValidationStatus.PENDING)));
		when(this.documentStorage.get("s3://motodigo-kyc/kyc/doc.pdf")).thenReturn(new byte[] { 1, 2, 3 });

		this.service.downloadDocumentForAdmin(documentId,
				new AdminAccess(actorId, "ADMIN", List.of("KYC_REVIEW_READ"), "corr-1"));

		verify(this.kycRepository).saveAudit(documentId, actorId, AccessAction.DOWNLOAD, "ADMIN", "corr-1");
		verify(this.documentStorage).get("s3://motodigo-kyc/kyc/doc.pdf");
	}

	private static AdminAccess access(String permission) {
		return new AdminAccess(UUID.randomUUID(), "ADMIN", List.of(permission), "corr-1");
	}

	private static KycProfileRecord profile(UUID profileId, UUID userId, ProfileType type, ValidationStatus status) {
		return new KycProfileRecord(profileId, userId, type, status, "Aline");
	}

	private static OfficialDocumentRecord document(UUID profileId) {
		return new OfficialDocumentRecord(UUID.randomUUID(), profileId, null, DocumentType.CNI,
				DocumentStatus.PENDING_REVIEW, "s3://bucket/doc.enc", "application/pdf", 10, null, Instant.now(),
				Instant.now());
	}

	private static ValidationDecisionRecord decision(UUID profileId, UUID actorId, KycDecision decision, String reason) {
		return new ValidationDecisionRecord(UUID.randomUUID(), profileId, actorId, decision, reason, Instant.now());
	}
}
