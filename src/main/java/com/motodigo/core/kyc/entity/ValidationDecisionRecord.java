package com.motodigo.core.kyc.entity;

import java.time.Instant;
import java.util.UUID;

public record ValidationDecisionRecord(
		UUID id,
		UUID profileId,
		UUID adminProfileId,
		KycDecision decision,
		String reason,
		Instant decidedAt) {
}
