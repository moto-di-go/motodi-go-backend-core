package com.motodigo.core.kyc.dto;

import com.motodigo.core.kyc.entity.KycDecision;
import jakarta.validation.constraints.NotNull;

public record AdminKycDecisionRequest(
		@NotNull KycDecision decision,
		String reason) {
}
