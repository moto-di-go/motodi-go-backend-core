package com.motodigo.core.kyc.entity;

import java.time.Instant;
import java.util.UUID;

public record OfficialDocumentRecord(
		UUID id,
		UUID profileId,
		UUID vehicleId,
		DocumentType type,
		DocumentStatus status,
		String encryptedStorageKey,
		String mimeType,
		long sizeBytes,
		String rejectionReason,
		Instant uploadedAt,
		Instant updatedAt) {
}
