package com.motodigo.core.kyc.entity;

import java.util.UUID;

import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;

public record KycProfileRecord(
		UUID id,
		UUID userId,
		ProfileType type,
		ValidationStatus validationStatus,
		String displayName) {
}
