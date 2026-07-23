package com.motodigo.core.kyc.dto;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.kyc.entity.OfficialDocumentRecord;
import com.motodigo.core.profile.entity.ValidationStatus;

public record UploadKycDocumentResponse(
		UUID profileId,
		ValidationStatus profileValidationStatus,
		OfficialDocumentResponse document,
		List<DocumentRequirementResponse> requirements) {

	public UploadKycDocumentResponse {
		requirements = List.copyOf(requirements);
	}

	public static UploadKycDocumentResponse from(UUID profileId, ValidationStatus profileValidationStatus,
			OfficialDocumentRecord document, List<DocumentRequirementResponse> requirements) {
		return new UploadKycDocumentResponse(profileId, profileValidationStatus, OfficialDocumentResponse.from(document),
				requirements);
	}
}
