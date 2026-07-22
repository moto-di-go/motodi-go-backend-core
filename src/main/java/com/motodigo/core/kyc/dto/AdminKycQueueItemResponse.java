package com.motodigo.core.kyc.dto;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;

public record AdminKycQueueItemResponse(
		UUID profileId,
		ProfileType profileType,
		ValidationStatus profileValidationStatus,
		String displayName,
		long documentsCount,
		long pendingDocumentsCount,
		Instant oldestPendingDocumentAt) {
}
