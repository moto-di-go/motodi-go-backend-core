package com.motodigo.core.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.motodigo.core.auth.config.AuthProperties;
import com.motodigo.core.auth.entity.UserRecord;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

	private final AuthProperties properties;
	private final JwtEncoder jwtEncoder;
	private final RandomTokenGenerator tokenGenerator;
	private final Sha256TokenHasher hasher;

	public TokenService(AuthProperties properties, JwtEncoder jwtEncoder, RandomTokenGenerator tokenGenerator,
			Sha256TokenHasher hasher) {
		this.properties = properties;
		this.jwtEncoder = jwtEncoder;
		this.tokenGenerator = tokenGenerator;
		this.hasher = hasher;
	}

	public AccessToken generateAccessToken(UserRecord user, UUID sessionId, List<String> roles,
			List<Map<String, Object>> profiles, UUID activeProfileId, String activeProfileType, List<String> permissions) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusSeconds(this.properties.accessTtlSeconds());
		JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
				.issuer(this.properties.issuerUrl())
				.audience(List.of(this.properties.audience()))
				.subject(user.id().toString())
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("sid", sessionId.toString())
				.claim("phone", user.phoneNumber())
				.claim("roles", roles)
				.claim("profiles", profiles)
				.claim("permissions", permissions);
		if (activeProfileId != null) {
			claimsBuilder.claim("activeProfileId", activeProfileId.toString());
		}
		if (activeProfileType != null) {
			claimsBuilder.claim("activeProfileType", activeProfileType);
		}
		JwtClaimsSet claims = claimsBuilder.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String value = this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(value, expiresAt, this.hasher.hash(value));
	}

	public RefreshToken generateRefreshToken() {
		String value = this.tokenGenerator.generateUrlSafeToken();
		Instant expiresAt = Instant.now().plusSeconds(this.properties.refreshTtlSeconds());
		return new RefreshToken(value, expiresAt, this.hasher.hash(value));
	}

	public record AccessToken(String value, Instant expiresAt, String hash) {
	}

	public record RefreshToken(String value, Instant expiresAt, String hash) {
	}
}
