package com.motodigo.core.kyc.dto;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.kyc.entity.DocumentDecisionRecord;
import com.motodigo.core.kyc.entity.DocumentType;
import com.motodigo.core.kyc.entity.KycDecision;

public record DocumentDecisionResponse(
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

	public static DocumentDecisionResponse from(DocumentDecisionRecord decision) {
		return new DocumentDecisionResponse(decision.id(), decision.profileId(), decision.documentId(),
				decision.documentType(), decision.fileName(), decision.mimeType(), decision.sizeBytes(),
				decision.decision(), decision.reason(), decision.adminProfileId(), decision.decidedAt());
	}
}
