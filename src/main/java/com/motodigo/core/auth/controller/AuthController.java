package com.motodigo.core.auth.controller;

import com.motodigo.core.auth.dto.ForgotPasswordRequest;
import com.motodigo.core.auth.dto.LoginRequest;
import com.motodigo.core.auth.dto.OtpResendRequest;
import com.motodigo.core.auth.dto.OtpVerifyRequest;
import com.motodigo.core.auth.dto.RefreshRequest;
import com.motodigo.core.auth.dto.RegisterRequest;
import com.motodigo.core.auth.dto.ResetPasswordRequest;
import com.motodigo.core.auth.service.AuthService;
import com.motodigo.core.auth.service.RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class AuthController {

	private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

	private final AuthService authService;

	AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
		return ResponseEntity.status(HttpStatus.CREATED).body(this.authService.register(request, metadata(servletRequest)));
	}

	@PostMapping("/otp/verify")
	ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerifyRequest request, HttpServletRequest servletRequest) {
		return ResponseEntity.ok(this.authService.verifyOtp(request, metadata(servletRequest)));
	}

	@PostMapping("/otp/resend")
	ResponseEntity<?> resendOtp(@Valid @RequestBody OtpResendRequest request, HttpServletRequest servletRequest) {
		return ResponseEntity.accepted().body(this.authService.resendOtp(request, metadata(servletRequest)));
	}

	@PostMapping("/login")
	ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		return ResponseEntity.ok(this.authService.login(request, metadata(servletRequest)));
	}

	@PostMapping("/refresh")
	ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
		return ResponseEntity.ok(this.authService.refresh(request, metadata(servletRequest)));
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
		this.authService.logout(jwt, metadata(servletRequest));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/password/forgot")
	ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest servletRequest) {
		this.authService.forgotPassword(request.phoneNumber(), metadata(servletRequest));
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/password/reset")
	ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
		this.authService.resetPassword(request, metadata(servletRequest));
		return ResponseEntity.noContent().build();
	}

	private static RequestMetadata metadata(HttpServletRequest request) {
		return new RequestMetadata(request.getRemoteAddr(), request.getHeader("User-Agent"),
				request.getHeader(CORRELATION_ID_HEADER));
	}
}
