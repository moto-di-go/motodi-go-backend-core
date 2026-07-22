package com.motodigo.core.kyc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.kyc.dto.KycDocumentsResponse;
import com.motodigo.core.kyc.entity.AccessAction;
import com.motodigo.core.kyc.entity.DocumentStatus;
import com.motodigo.core.kyc.entity.DocumentType;
import com.motodigo.core.kyc.entity.KycProfileRecord;
import com.motodigo.core.kyc.entity.OfficialDocumentRecord;
import com.motodigo.core.kyc.exception.KycErrorCode;
import com.motodigo.core.kyc.exception.KycException;
import com.motodigo.core.kyc.repository.KycRepository;
import com.motodigo.core.kyc.service.FileValidationService.ValidatedFile;
import com.motodigo.core.kyc.service.KycService.AdminAccess;
import com.motodigo.core.kyc.storage.DocumentStorage;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import com.motodigo.core.profile.repository.ProfileRepository;
import com.motodigo.core.profile.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

	@Mock
	private ProfileRepository profileRepository;
	@Mock
	private PermissionService permissionService;
	@Mock
	private KycRepository kycRepository;
	@Mock
	private FileValidationService fileValidationService;
	@Mock
	private KycCryptoService cryptoService;
	@Mock
	private DocumentStorage documentStorage;

	private KycService service;

	@BeforeEach
	void setUp() {
		this.service = new KycService(this.profileRepository, this.permissionService, this.kycRepository,
				this.fileValidationService, this.cryptoService, this.documentStorage);
	}

	@Test
	void listCurrentDocumentsReturnsDriverRequirements() {
		UUID userId = UUID.randomUUID();
		ProfileRecord profile = profile(userId, ProfileType.MOTOTAXI, ValidationStatus.PENDING);
		when(this.profileRepository.findActiveByUserId(userId)).thenReturn(Optional.of(profile));
		when(this.permissionService.allowedActions(profile)).thenReturn(List.of("KYC_UPLOAD"));
		when(this.kycRepository.findDocumentsByProfile(profile.id())).thenReturn(List.of(document(profile.id(), DocumentType.CNI)));

		KycDocumentsResponse response = this.service.listCurrentUserDocuments(userId);

		assertThat(response.requirements()).hasSize(5);
		assertThat(response.requirements()).anyMatch(requirement -> requirement.type() == DocumentType.CNI
				&& requirement.uploaded() && requirement.latestStatus() == DocumentStatus.PENDING_REVIEW);
		assertThat(response.requirements()).anyMatch(requirement -> requirement.type() == DocumentType.INSURANCE
				&& !requirement.uploaded());
	}

	@Test
	void uploadRejectsProfileWithoutKycUploadPermission() {
		UUID userId = UUID.randomUUID();
		ProfileRecord profile = profile(userId, ProfileType.MOTOTAXI, ValidationStatus.VALIDATED);
		when(this.profileRepository.findActiveByUserId(userId)).thenReturn(Optional.of(profile));
		when(this.permissionService.allowedActions(profile)).thenReturn(List.of("PROFILE_READ"));

		assertThatThrownBy(() -> this.service.uploadDocument(userId, DocumentType.CNI, null, file()))
				.isInstanceOf(KycException.class)
				.extracting("errorCode")
				.isEqualTo(KycErrorCode.KYC_ACCESS_DENIED);
	}

	@Test
	void uploadRejectsUnsupportedProfile() {
		UUID userId = UUID.randomUUID();
		when(this.profileRepository.findActiveByUserId(userId))
				.thenReturn(Optional.of(profile(userId, ProfileType.AGENCY, ValidationStatus.PENDING)));

		assertThatThrownBy(() -> this.service.uploadDocument(userId, DocumentType.CNI, null, file()))
				.isInstanceOf(KycException.class)
				.extracting("errorCode")
				.isEqualTo(KycErrorCode.KYC_PROFILE_NOT_ELIGIBLE);
	}

	@Test
	void uploadEncryptsBeforeStorageAndSavesMetadata() {
		UUID userId = UUID.randomUUID();
		ProfileRecord profile = profile(userId, ProfileType.TRANSPORTER, ValidationStatus.PENDING);
		byte[] plaintext = "pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] encrypted = "encrypted".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		OfficialDocumentRecord saved = document(profile.id(), DocumentType.CNI);
		when(this.profileRepository.findActiveByUserId(userId)).thenReturn(Optional.of(profile));
		when(this.permissionService.allowedActions(profile)).thenReturn(List.of("KYC_UPLOAD"));
		when(this.fileValidationService.validate(any())).thenReturn(new ValidatedFile("application/pdf", plaintext));
		when(this.cryptoService.checksum(plaintext)).thenReturn("checksum");
		when(this.cryptoService.encrypt(plaintext)).thenReturn(encrypted);
		when(this.documentStorage.put(any(), eq(encrypted), eq("application/pdf"))).thenReturn("s3://bucket/doc.enc");
		when(this.kycRepository.saveDocument(eq(profile.id()), eq(null), eq(DocumentType.CNI), eq("s3://bucket/doc.enc"),
				eq("application/pdf"), eq(3L), eq("checksum"))).thenReturn(saved);
		when(this.kycRepository.findDocumentsByProfile(profile.id())).thenReturn(List.of(saved));

		this.service.uploadDocument(userId, DocumentType.CNI, null, file());

		InOrder order = inOrder(this.cryptoService, this.documentStorage, this.kycRepository);
		order.verify(this.cryptoService).encrypt(plaintext);
		order.verify(this.documentStorage).put(any(), eq(encrypted), eq("application/pdf"));
		order.verify(this.kycRepository).saveDocument(any(), any(), any(), any(), any(), eq(3L), any());
	}

	@Test
	void uploadTranslatesDuplicateChecksum() {
		UUID userId = UUID.randomUUID();
		ProfileRecord profile = profile(userId, ProfileType.MOTOTAXI, ValidationStatus.PENDING);
		when(this.profileRepository.findActiveByUserId(userId)).thenReturn(Optional.of(profile));
		when(this.permissionService.allowedActions(profile)).thenReturn(List.of("KYC_UPLOAD"));
		when(this.fileValidationService.validate(any())).thenReturn(new ValidatedFile("application/pdf", new byte[] {1, 2, 3}));
		when(this.cryptoService.checksum(any())).thenReturn("checksum");
		when(this.cryptoService.encrypt(any())).thenReturn(new byte[] {9});
		when(this.documentStorage.put(any(), any(), any())).thenReturn("s3://bucket/doc.enc");
		when(this.kycRepository.saveDocument(any(), any(), any(), any(), any(), eq(3L), any()))
				.thenThrow(new org.springframework.dao.DuplicateKeyException("uq_official_documents_checksum"));

		assertThatThrownBy(() -> this.service.uploadDocument(userId, DocumentType.CNI, null, file()))
				.isInstanceOf(KycException.class)
				.extracting("errorCode")
				.isEqualTo(KycErrorCode.DOCUMENT_DUPLICATE);
	}

	@Test
	void adminReadRequiresPermissionAndAudits() {
		UUID profileId = UUID.randomUUID();
		UUID actorProfileId = UUID.randomUUID();
		OfficialDocumentRecord document = document(profileId, DocumentType.CNI);
		when(this.kycRepository.findProfileById(profileId))
				.thenReturn(Optional.of(new KycProfileRecord(profileId, UUID.randomUUID(), ProfileType.MOTOTAXI,
						ValidationStatus.PENDING, "Aline")));
		when(this.kycRepository.findDocumentsByProfile(profileId)).thenReturn(List.of(document));

		this.service.listDocumentsForAdmin(profileId,
				new AdminAccess(actorProfileId, "ADMIN", List.of("KYC_REVIEW_READ"), "corr-1"));

		verify(this.kycRepository).saveAudit(document.id(), actorProfileId, AccessAction.VIEW, "ADMIN", "corr-1");
		assertThatThrownBy(() -> this.service.listDocumentsForAdmin(profileId,
				new AdminAccess(actorProfileId, "ADMIN", List.of(), "corr-1")))
				.isInstanceOf(KycException.class)
				.extracting("errorCode")
				.isEqualTo(KycErrorCode.KYC_ACCESS_DENIED);
	}

	@Test
	void downloadDoesNotReadStorageWhenAuditFails() {
		UUID profileId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		OfficialDocumentRecord document = document(documentId, profileId, DocumentType.CNI);
		when(this.kycRepository.findDocumentById(documentId)).thenReturn(Optional.of(document));
		when(this.kycRepository.findProfileById(profileId))
				.thenReturn(Optional.of(new KycProfileRecord(profileId, UUID.randomUUID(), ProfileType.MOTOTAXI,
						ValidationStatus.PENDING, "Aline")));
		org.springframework.dao.DataAccessResourceFailureException failure =
				new org.springframework.dao.DataAccessResourceFailureException("audit failed");
		org.mockito.Mockito.doThrow(failure).when(this.kycRepository)
				.saveAudit(eq(documentId), any(), eq(AccessAction.DOWNLOAD), any(), any());

		assertThatThrownBy(() -> this.service.downloadDocumentForAdmin(documentId,
				new AdminAccess(UUID.randomUUID(), "ADMIN", List.of("KYC_REVIEW_READ"), "corr-1")))
				.isSameAs(failure);
		verify(this.documentStorage, never()).get(any());
	}

	private static MockMultipartFile file() {
		return new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.7".getBytes());
	}

	private static ProfileRecord profile(UUID userId, ProfileType type, ValidationStatus status) {
		return new ProfileRecord(UUID.randomUUID(), userId, type, status, "Aline", null, BigDecimal.ZERO, true,
				Instant.now(), Instant.now(), null, null, null, null, null, null);
	}

	private static OfficialDocumentRecord document(UUID profileId, DocumentType type) {
		return document(UUID.randomUUID(), profileId, type);
	}

	private static OfficialDocumentRecord document(UUID documentId, UUID profileId, DocumentType type) {
		return new OfficialDocumentRecord(documentId, profileId, null, type, DocumentStatus.PENDING_REVIEW,
				"s3://bucket/doc.enc", "application/pdf", 10, "checksum", null, Instant.now(), Instant.now());
	}
}
