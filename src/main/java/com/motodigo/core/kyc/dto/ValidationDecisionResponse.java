package com.motodigo.core.kyc.dto;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.kyc.entity.KycDecision;
import com.motodigo.core.kyc.entity.ValidationDecisionRecord;

public record ValidationDecisionResponse(
		UUID id,
		UUID profileId,
		UUID adminProfileId,
		KycDecision decision,
		String reason,
		Instant decidedAt) {

	public static ValidationDecisionResponse from(ValidationDecisionRecord decision) {
		return new ValidationDecisionResponse(decision.id(), decision.profileId(), decision.adminProfileId(),
				decision.decision(), decision.reason(), decision.decidedAt());
	}
}
