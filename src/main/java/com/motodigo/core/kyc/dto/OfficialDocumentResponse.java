package com.motodigo.core.kyc.dto;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.kyc.entity.DocumentStatus;
import com.motodigo.core.kyc.entity.DocumentType;
import com.motodigo.core.kyc.entity.OfficialDocumentRecord;

public record OfficialDocumentResponse(
		UUID id,
		UUID profileId,
		UUID vehicleId,
		DocumentType type,
		DocumentStatus status,
		String mimeType,
		long sizeBytes,
		String rejectionReason,
		Instant uploadedAt,
		Instant updatedAt) {

	public static OfficialDocumentResponse from(OfficialDocumentRecord document) {
		return new OfficialDocumentResponse(
				document.id(),
				document.profileId(),
				document.vehicleId(),
				document.type(),
				document.status(),
				document.mimeType(),
				document.sizeBytes(),
				document.rejectionReason(),
				document.uploadedAt(),
				document.updatedAt());
	}
}
