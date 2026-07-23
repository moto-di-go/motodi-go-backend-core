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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

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
				DocumentStatus.PENDING_REVIEW, "s3://motodigo-kyc/kyc/doc.pdf", "doc.pdf", "application/pdf", 10, null,
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

	@Test
	void listCurrentDocumentsBuildsRequirementsForActiveOwner() {
		UUID userId = UUID.randomUUID();
		UUID profileId = UUID.randomUUID();
		when(this.kycRepository.findProfileById(profileId))
				.thenReturn(Optional.of(profile(profileId, userId, ProfileType.MOTOTAXI, ValidationStatus.PENDING)));
		when(this.kycRepository.findDocumentsByProfile(profileId)).thenReturn(List.of(
				document(profileId, DocumentType.CNI, DocumentStatus.ACCEPTED),
				document(profileId, DocumentType.PROFILE_PHOTO, DocumentStatus.REJECTED)));

		var response = this.service.listCurrentDocuments(userId, profileId);

		Assertions.assertThat(response.requirements()).hasSize(5);
		Assertions.assertThat(response.requirements())
				.anySatisfy(requirement -> {
					Assertions.assertThat(requirement.type()).isEqualTo(DocumentType.CNI);
					Assertions.assertThat(requirement.uploaded()).isTrue();
				})
				.anySatisfy(requirement -> {
					Assertions.assertThat(requirement.type()).isEqualTo(DocumentType.PROFILE_PHOTO);
					Assertions.assertThat(requirement.uploaded()).isFalse();
					Assertions.assertThat(requirement.latestStatus()).isEqualTo(DocumentStatus.REJECTED);
				});
	}

	@Test
	void uploadRejectsInvalidFileBeforeStorage() {
		UUID userId = UUID.randomUUID();
		UUID profileId = UUID.randomUUID();
		when(this.kycRepository.findProfileById(profileId))
				.thenReturn(Optional.of(profile(profileId, userId, ProfileType.MOTOTAXI, ValidationStatus.PENDING)));

		MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "bad".getBytes());

		assertThatThrownBy(() -> this.service.uploadDocument(userId, profileId, DocumentType.CNI, null, file))
				.isInstanceOf(KycException.class)
				.extracting("errorCode")
				.isEqualTo(KycErrorCode.KYC_DOCUMENT_INVALID);

		verify(this.documentStorage, never()).put(any(), any(), any());
	}

	@Test
	void uploadMapsDuplicateChecksumToDuplicateDocumentError() {
		UUID userId = UUID.randomUUID();
		UUID profileId = UUID.randomUUID();
		MockMultipartFile file = new MockMultipartFile("file", "permis.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
		when(this.kycRepository.findProfileById(profileId))
				.thenReturn(Optional.of(profile(profileId, userId, ProfileType.MOTOTAXI, ValidationStatus.PENDING)));
		when(this.documentStorage.put(any(), any(byte[].class), eq("image/jpeg")))
				.thenReturn("s3://bucket/kyc/doc.enc");
		when(this.kycRepository.saveDocument(eq(profileId), eq(null), eq(DocumentType.DRIVER_LICENSE),
				eq(DocumentStatus.PENDING_REVIEW), eq("s3://bucket/kyc/doc.enc"), eq("permis.jpg"),
				eq("image/jpeg"), eq(3L), any())).thenThrow(new DuplicateKeyException("duplicate checksum"));

		assertThatThrownBy(() -> this.service.uploadDocument(userId, profileId, DocumentType.DRIVER_LICENSE, null, file))
				.isInstanceOf(KycException.class)
				.extracting("errorCode")
				.isEqualTo(KycErrorCode.KYC_DOCUMENT_DUPLICATE);
	}

	@Test
	void uploadStoresDocumentAndMarksRejectedProfilePendingWhenComplete() {
		UUID userId = UUID.randomUUID();
		UUID profileId = UUID.randomUUID();
		MockMultipartFile file = new MockMultipartFile("file", "carte-grise.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
		OfficialDocumentRecord saved = document(profileId, DocumentType.VEHICLE_REGISTRATION,
				DocumentStatus.PENDING_REVIEW);
		when(this.kycRepository.findProfileById(profileId))
				.thenReturn(Optional.of(profile(profileId, userId, ProfileType.TRANSPORTER, ValidationStatus.REJECTED)));
		when(this.documentStorage.put(any(), any(byte[].class), eq("image/jpeg")))
				.thenReturn("s3://bucket/kyc/doc.enc");
		when(this.kycRepository.saveDocument(eq(profileId), eq(null), eq(DocumentType.VEHICLE_REGISTRATION),
				eq(DocumentStatus.PENDING_REVIEW), eq("s3://bucket/kyc/doc.enc"), eq("carte-grise.jpg"),
				eq("image/jpeg"), eq(3L), any())).thenReturn(saved);
		when(this.kycRepository.findDocumentsByProfile(profileId)).thenReturn(List.of(
				document(profileId, DocumentType.CNI, DocumentStatus.ACCEPTED),
				document(profileId, DocumentType.PROFILE_PHOTO, DocumentStatus.ACCEPTED),
				document(profileId, DocumentType.DRIVER_LICENSE, DocumentStatus.ACCEPTED),
				saved,
				document(profileId, DocumentType.INSURANCE, DocumentStatus.ACCEPTED)));

		var response = this.service.uploadDocument(userId, profileId, DocumentType.VEHICLE_REGISTRATION, null, file);

		Assertions.assertThat(response.profileValidationStatus()).isEqualTo(ValidationStatus.PENDING);
		verify(this.kycRepository).updateProfileStatus(profileId, ValidationStatus.PENDING);
	}

	private static AdminAccess access(String permission) {
		return new AdminAccess(UUID.randomUUID(), "ADMIN", List.of(permission), "corr-1");
	}

	private static KycProfileRecord profile(UUID profileId, UUID userId, ProfileType type, ValidationStatus status) {
		return new KycProfileRecord(profileId, userId, type, status, "Aline");
	}

	private static OfficialDocumentRecord document(UUID profileId) {
		return document(profileId, DocumentType.CNI, DocumentStatus.PENDING_REVIEW);
	}

	private static OfficialDocumentRecord document(UUID profileId, DocumentType type, DocumentStatus status) {
		return new OfficialDocumentRecord(UUID.randomUUID(), profileId, null, type,
				status, "s3://bucket/doc.enc", type.name().toLowerCase() + ".jpg", "image/jpeg", 10, null, Instant.now(),
				Instant.now());
	}

	private static ValidationDecisionRecord decision(UUID profileId, UUID actorId, KycDecision decision, String reason) {
		return new ValidationDecisionRecord(UUID.randomUUID(), profileId, actorId, decision, reason, Instant.now());
	}
}
