package com.motodigo.core.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthTokenResponse(
		String tokenType,
		String accessToken,
		String refreshToken,
		Instant accessTokenExpiresAt,
		Instant refreshTokenExpiresAt,
		UUID sessionId,
		UUID userId,
		String phoneNumber,
		List<String> roles,
		List<ProfileTokenResponse> profiles,
		UUID activeProfileId,
		String activeProfileType,
		List<String> permissions) {
}
