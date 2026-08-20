package com.motodigo.core.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.auth.dto.AuthTokenResponse;
import com.motodigo.core.auth.dto.OtpVerifyResponse;
import com.motodigo.core.auth.dto.RegisterResponse;
import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.auth.exception.AuthExceptionHandler;
import com.motodigo.core.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

	@Mock
	private AuthService authService;

	private MockMvc mockMvc;
	private AuthController controller;

	@BeforeEach
	void setUp() {
		this.controller = new AuthController(this.authService);
		MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json().build());
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.controller)
				.setControllerAdvice(new AuthExceptionHandler())
				.setMessageConverters(jsonConverter)
				.build();
	}

	@Test
	void registerReturnsCreated() throws Exception {
		UUID userId = UUID.randomUUID();
		when(this.authService.register(any(), any()))
				.thenReturn(new RegisterResponse(userId, "+237699001122", "PENDING_PHONE_VERIFICATION",
						Instant.parse("2026-07-09T16:30:00Z")));

		this.mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"phoneNumber":"699001122","password":"secret123","fullName":"Aline","cguAccepted":true}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(userId.toString()))
				.andExpect(jsonPath("$.phoneNumber").value("+237699001122"));
	}

	@Test
	void verifyOtpReturnsOk() throws Exception {
		UUID userId = UUID.randomUUID();
		when(this.authService.verifyOtp(any(), any()))
				.thenReturn(new OtpVerifyResponse(userId, "+237699001122", "ACTIVE"));

		this.mockMvc.perform(post("/api/v1/auth/otp/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"phoneNumber":"699001122","code":"123456","purpose":"REGISTRATION"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void loginInvalidReturnsStableError() throws Exception {
		when(this.authService.login(any(), any())).thenThrow(new AuthException(AuthErrorCode.AUTH_INVALID));

		this.mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Correlation-Id", "corr-auth-test")
						.content("""
								{"phoneNumber":"699001122","password":"bad"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("AUTH_INVALID"))
				.andExpect(jsonPath("$.correlationId").value("corr-auth-test"));
	}

	@Test
	void refreshReturnsTokens() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		when(this.authService.refresh(any(), any()))
				.thenReturn(new AuthTokenResponse("Bearer", "access", "refresh", Instant.now().plusSeconds(60),
						Instant.now().plusSeconds(3600), sessionId, userId, "+237699001122", List.of(), List.of(), null,
						null, List.of()));

		this.mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"opaque-refresh"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.sessionId").value(sessionId.toString()));
	}

	@Test
	void forgotPasswordReturnsAccepted() throws Exception {
		this.mockMvc.perform(post("/api/v1/auth/password/forgot")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"phoneNumber":"699001122"}
								"""))
				.andExpect(status().isAccepted());
	}

	@Test
	void resetPasswordReturnsNoContent() throws Exception {
		this.mockMvc.perform(post("/api/v1/auth/password/reset")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"phoneNumber":"699001122","code":"123456","newPassword":"newSecret123"}
								"""))
				.andExpect(status().isNoContent());
	}

	@Test
	void logoutRevokesUsingJwtPrincipal() {
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.subject(UUID.randomUUID().toString())
				.claim("sid", UUID.randomUUID().toString())
				.claim("phone", "+237699001122")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60))
				.build();

		this.controller.logout(jwt, new org.springframework.mock.web.MockHttpServletRequest());

		verify(this.authService).logout(any(), any());
	}
}
