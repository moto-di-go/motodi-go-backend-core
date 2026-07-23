package com.motodigo.core.kyc.dto;

import com.motodigo.core.kyc.entity.DocumentStatus;
import com.motodigo.core.kyc.entity.DocumentType;

public record DocumentRequirementResponse(
		DocumentType type,
		boolean required,
		boolean uploaded,
		DocumentStatus latestStatus) {
}
