package com.motodigo.core.kyc.dto;

import java.util.UUID;

import com.motodigo.core.kyc.entity.KycDecision;
import com.motodigo.core.profile.entity.ValidationStatus;

public record AdminKycDecisionResponse(
		UUID profileId,
		ValidationStatus profileValidationStatus,
		KycDecision decision,
		ValidationDecisionResponse decisionRecord) {
}
