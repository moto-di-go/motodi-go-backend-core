package com.motodigo.core.auth.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class AuthSecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	JwtEncoder jwtEncoder(AuthProperties properties) {
		byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
		return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secret));
	}

	@Bean
	JwtDecoder jwtDecoder(AuthProperties properties) {
		byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
		SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
		return NimbusJwtDecoder.withSecretKey(key)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<ActiveSessionFilter> activeSessionFilter)
			throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
						.requestMatchers(HttpMethod.POST,
								"/api/v1/auth/register",
								"/api/v1/auth/otp/verify",
								"/api/v1/auth/otp/resend",
								"/api/v1/auth/login",
								"/api/v1/auth/refresh",
								"/api/v1/auth/password/forgot",
								"/api/v1/auth/password/reset").permitAll()
						.requestMatchers(HttpMethod.POST,
								"/api/v1/integrations/mobile-money/callback",
								"/api/v1/integrations/mobile-money/*/callback").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
		activeSessionFilter.ifAvailable(filter -> http.addFilterAfter(filter, BearerTokenAuthenticationFilter.class));
		return http.build();
	}
}
