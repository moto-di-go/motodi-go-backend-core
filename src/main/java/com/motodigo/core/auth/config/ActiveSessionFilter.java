package com.motodigo.core.auth.config;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motodigo.core.auth.dto.ErrorResponse;
import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.repository.SessionRepository;
import com.motodigo.core.auth.service.Sha256TokenHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class ActiveSessionFilter extends OncePerRequestFilter {

	private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

	private final ObjectProvider<SessionRepository> sessionRepository;
	private final Sha256TokenHasher hasher;
	private final ObjectMapper objectMapper;

	ActiveSessionFilter(ObjectProvider<SessionRepository> sessionRepository, Sha256TokenHasher hasher, ObjectMapper objectMapper) {
		this.sessionRepository = sessionRepository;
		this.hasher = hasher;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
			filterChain.doFilter(request, response);
			return;
		}

		Jwt jwt = jwtAuthentication.getToken();
		if (!isCurrentAccessTokenActive(jwt)) {
			reject(response, request.getHeader(CORRELATION_ID_HEADER));
			return;
		}

		filterChain.doFilter(request, response);
	}

	private boolean isCurrentAccessTokenActive(Jwt jwt) {
		String sid = jwt.getClaimAsString("sid");
		if (sid == null || sid.isBlank()) {
			return false;
		}
		SessionRepository repository = this.sessionRepository.getIfAvailable();
		if (repository == null) {
			return false;
		}
		try {
			return repository.isActiveAccessToken(UUID.fromString(sid), this.hasher.hash(jwt.getTokenValue()));
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private void reject(HttpServletResponse response, String correlationId) throws IOException {
		response.setStatus(AuthErrorCode.SESSION_EXPIRED.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		if (correlationId != null && !correlationId.isBlank()) {
			response.setHeader(CORRELATION_ID_HEADER, correlationId);
		}
		ErrorResponse error = new ErrorResponse(
				AuthErrorCode.SESSION_EXPIRED.name(),
				AuthErrorCode.SESSION_EXPIRED.message(),
				correlationId,
				Instant.now());
		this.objectMapper.writeValue(response.getOutputStream(), error);
	}
}
