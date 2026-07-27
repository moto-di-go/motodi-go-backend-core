package com.motodigo.core.kyc.entity;

import java.time.Instant;
import java.util.UUID;

public record DocumentDecisionRecord(
		UUID id,
		UUID profileId,
		UUID documentId,
		DocumentType documentType,
		String fileName,
		String mimeType,
		long sizeBytes,
		KycDecision decision,
		String reason,
		UUID adminProfileId,
		Instant decidedAt) {
}
