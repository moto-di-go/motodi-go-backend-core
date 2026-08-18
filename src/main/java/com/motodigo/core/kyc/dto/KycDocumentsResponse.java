package com.motodigo.core.kyc.dto;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.kyc.entity.OfficialDocumentRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;

public record KycDocumentsResponse(
		UUID profileId,
		ProfileType profileType,
		ValidationStatus profileValidationStatus,
		List<DocumentRequirementResponse> requirements,
		List<OfficialDocumentResponse> documents) {

	public KycDocumentsResponse {
		requirements = List.copyOf(requirements);
		documents = List.copyOf(documents);
	}

	public static KycDocumentsResponse from(UUID profileId, ProfileType profileType,
			ValidationStatus profileValidationStatus, List<DocumentRequirementResponse> requirements,
			List<OfficialDocumentRecord> documents) {
		return new KycDocumentsResponse(profileId, profileType, profileValidationStatus, requirements,
				documents.stream().map(OfficialDocumentResponse::from).toList());
	}
}
