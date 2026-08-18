package com.motodigo.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.motodigo.core.auth.dto.AuthTokenResponse;
import com.motodigo.core.auth.dto.RegisterResponse;
import com.motodigo.core.auth.service.OtpSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthFlowIT {

	private static final DockerImageName POSTGIS_IMAGE = DockerImageName.parse("postgis/postgis:16-3.4")
			.asCompatibleSubstituteFor("postgres");
	private static final CapturingOtpSender OTP_SENDER = new CapturingOtpSender();

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGIS_IMAGE)
			.withDatabaseName("motodigo")
			.withUsername("motodigo")
			.withPassword("motodigo");

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtDecoder jwtDecoder;

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("motodigo.auth.jwt-secret", () -> "test-jwt-secret-at-least-32-bytes-long");
		registry.add("motodigo.otp.pepper", () -> "test-otp-pepper-at-least-16-bytes");
		registry.add("motodigo.otp.provider", () -> "test");
	}

	@BeforeEach
	void resetOtpSender() {
		OTP_SENDER.clear();
	}

	@Test
	void registerVerifyLoginRefreshLogoutAndResetPasswordAgainstPostgis() {
		var register = this.restTemplate.postForEntity(url("/api/v1/auth/register"), Map.of(
				"phoneNumber", "699001122",
				"password", "OldSecret123",
				"fullName", "Aline Test",
				"locale", "fr-CM",
				"cguAccepted", true), RegisterResponse.class);

		assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(register.getBody()).isNotNull();
		assertThat(register.getBody().phoneNumber()).isEqualTo("+237699001122");

		var duplicate = this.restTemplate.postForEntity(url("/api/v1/auth/register"), Map.of(
				"phoneNumber", "+237699001122",
				"password", "OldSecret123",
				"fullName", "Duplicate",
				"cguAccepted", true), Map.class);
		assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(duplicate.getBody()).containsEntry("errorCode", "PHONE_ALREADY_EXISTS");

		verifyOtp("699001122", latestCode("+237699001122"), "REGISTRATION");
		assertThat(walletCount(register.getBody().userId().toString())).isEqualTo(1);

		AuthTokenResponse login = login("699001122", "OldSecret123", HttpStatus.OK).getBody();
		assertThat(login).isNotNull();
		assertThat(login.accessToken()).isNotBlank();
		assertThat(login.refreshToken()).isNotBlank();

		var refresh = this.restTemplate.postForEntity(url("/api/v1/auth/refresh"), Map.of(
				"refreshToken", login.refreshToken(),
				"deviceId", "it-device-refresh"), AuthTokenResponse.class);
		assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(refresh.getBody()).isNotNull();
		assertThat(refresh.getBody().refreshToken()).isNotEqualTo(login.refreshToken());

		assertThat(logout(refresh.getBody().accessToken()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		var replayLogout = logout(refresh.getBody().accessToken());
		assertThat(replayLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		var forgotUnknown = this.restTemplate.postForEntity(url("/api/v1/auth/password/forgot"),
				Map.of("phoneNumber", "699009999"), Void.class);
		assertThat(forgotUnknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

		var forgot = this.restTemplate.postForEntity(url("/api/v1/auth/password/forgot"),
				Map.of("phoneNumber", "699001122"), Void.class);
		assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

		var reset = this.restTemplate.postForEntity(url("/api/v1/auth/password/reset"), Map.of(
				"phoneNumber", "699001122",
				"code", latestCode("+237699001122"),
				"newPassword", "NewSecret123"), Void.class);
		assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		login("699001122", "OldSecret123", HttpStatus.UNAUTHORIZED);
		assertThat(login("699001122", "NewSecret123", HttpStatus.OK).getBody()).isNotNull();
	}

	@Test
	void authenticatedUserCanCreateUpdateSwitchAndRefreshProfileClaims() {
		var register = this.restTemplate.postForEntity(url("/api/v1/auth/register"), Map.of(
				"phoneNumber", "699001133",
				"password", "Secret1234",
				"fullName", "Aline Profiles",
				"cguAccepted", true), RegisterResponse.class);
		assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		verifyOtp("699001133", latestCode("+237699001133"), "REGISTRATION");

		AuthTokenResponse login = login("699001133", "Secret1234", HttpStatus.OK).getBody();
		assertThat(login).isNotNull();

		var unauthenticated = this.restTemplate.getForEntity(url("/api/v1/profiles"), Map.class);
		assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		Map<?, ?> passenger = createProfile(login.accessToken(), Map.of(
				"type", "PASSENGER",
				"displayName", "Aline Passenger",
				"passenger", Map.of(
						"preferredLanguage", "fr",
						"emergencyContact", "+237699000001")));
		assertThat(passenger.get("type")).isEqualTo("PASSENGER");
		assertThat(passenger.get("validationStatus")).isEqualTo("PENDING");
		assertThat(passenger.get("active")).isEqualTo(true);
		assertThat(passenger.get("allowedActions").toString()).contains("PASSENGER_SEARCH_RIDE");
		assertThat(passenger.get("allowedActions").toString()).contains("KYC_UPLOAD");

		String licenseNumber = "LIC-" + UUID.randomUUID().toString().substring(0, 8);
		Map<?, ?> motoTaxi = createProfile(login.accessToken(), Map.of(
				"type", "MOTOTAXI",
				"displayName", "Aline Moto",
				"driver", Map.of("gpsConsent", true),
				"motoTaxi", Map.of(
						"licenseNumber", licenseNumber,
						"operatingZone", "Yaounde")));
		assertThat(motoTaxi.get("type")).isEqualTo("MOTOTAXI");
		assertThat(motoTaxi.get("validationStatus")).isEqualTo("PENDING");
		assertThat(motoTaxi.get("active")).isEqualTo(false);
		assertThat(motoTaxi.get("allowedActions").toString()).contains("KYC_UPLOAD");

		var profiles = exchange(login.accessToken(), HttpMethod.GET, "/api/v1/profiles", null, List.class);
		assertThat(profiles.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(profiles.getBody()).hasSize(2);

		String motoTaxiId = String.valueOf(motoTaxi.get("id"));
		var update = exchange(login.accessToken(), HttpMethod.PATCH, "/api/v1/profiles/" + motoTaxiId, Map.of(
				"displayName", "Aline Moto Updated",
				"motoTaxi", Map.of("operatingZone", "Douala")), Map.class);
		assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(update.getBody().get("displayName")).isEqualTo("Aline Moto Updated");
		assertThat(((Map<?, ?>) update.getBody().get("motoTaxi")).get("operatingZone")).isEqualTo("Douala");

		var activated = exchange(login.accessToken(), HttpMethod.PATCH, "/api/v1/profiles/active/" + motoTaxiId, null,
				Map.class);
		assertThat(activated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(activated.getBody().get("id")).isEqualTo(motoTaxiId);
		assertThat(activated.getBody().get("active")).isEqualTo(true);

		var current = exchange(login.accessToken(), HttpMethod.GET, "/api/v1/profiles/current", null, Map.class);
		assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(current.getBody().get("id")).isEqualTo(motoTaxiId);

		var refresh = this.restTemplate.postForEntity(url("/api/v1/auth/refresh"), Map.of(
				"refreshToken", login.refreshToken(),
				"deviceId", "it-device-profile-refresh"), AuthTokenResponse.class);
		assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(refresh.getBody()).isNotNull();
		assertThat(refresh.getBody().roles()).contains("PASSENGER", "MOTOTAXI");
		assertThat(refresh.getBody().profiles()).hasSize(2);
		assertThat(refresh.getBody().activeProfileId()).isEqualTo(UUID.fromString(motoTaxiId));
		assertThat(refresh.getBody().activeProfileType()).isEqualTo("MOTOTAXI");
		assertThat(refresh.getBody().permissions()).contains("KYC_UPLOAD");

		var jwt = this.jwtDecoder.decode(refresh.getBody().accessToken());
		assertThat(jwt.getClaimAsStringList("roles")).contains("PASSENGER", "MOTOTAXI");
		assertThat(jwt.getClaimAsString("activeProfileId")).isEqualTo(motoTaxiId);
		assertThat(jwt.getClaimAsString("activeProfileType")).isEqualTo("MOTOTAXI");
		assertThat(jwt.getClaimAsStringList("permissions")).contains("KYC_UPLOAD");
		assertThat((List<?>) jwt.getClaim("profiles")).hasSize(2);
	}

	private void verifyOtp(String phoneNumber, String code, String purpose) {
		var response = this.restTemplate.postForEntity(url("/api/v1/auth/otp/verify"), Map.of(
				"phoneNumber", phoneNumber,
				"code", code,
				"purpose", purpose), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("status", "ACTIVE");
	}

	private org.springframework.http.ResponseEntity<AuthTokenResponse> login(String phoneNumber, String password,
			HttpStatus expectedStatus) {
		var response = this.restTemplate.postForEntity(url("/api/v1/auth/login"), Map.of(
				"phoneNumber", phoneNumber,
				"password", password,
				"deviceId", "it-device"), AuthTokenResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
		return response;
	}

	private org.springframework.http.ResponseEntity<Void> logout(String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		return this.restTemplate.exchange(url("/api/v1/auth/logout"), HttpMethod.POST, new HttpEntity<>(headers), Void.class);
	}

	private Map<?, ?> createProfile(String accessToken, Map<String, Object> body) {
		var response = exchange(accessToken, HttpMethod.POST, "/api/v1/profiles", body, Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		return response.getBody();
	}

	private <T> org.springframework.http.ResponseEntity<T> exchange(String accessToken, HttpMethod method, String path,
			Object body, Class<T> responseType) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return this.restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), responseType);
	}

	private String latestCode(String phoneNumber) {
		return OTP_SENDER.latestCode(phoneNumber);
	}

	private int walletCount(String userId) {
		Integer count = this.jdbcTemplate.queryForObject("SELECT count(*) FROM wallets WHERE user_id = ?",
				Integer.class, java.util.UUID.fromString(userId));
		return count == null ? 0 : count;
	}

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}

	@TestConfiguration
	static class OtpSenderConfig {

		@Bean
		@Primary
		OtpSender otpSender() {
			return OTP_SENDER;
		}
	}

	private static final class CapturingOtpSender implements OtpSender {

		private static final Pattern OTP_PATTERN = Pattern.compile(" est ([0-9]{4,10})\\.");
		private final List<OtpMessage> messages = new CopyOnWriteArrayList<>();

		@Override
		public void sendOtp(String phoneNumber, String message) {
			this.messages.add(new OtpMessage(phoneNumber, message));
		}

		void clear() {
			this.messages.clear();
		}

		String latestCode(String phoneNumber) {
			for (int i = this.messages.size() - 1; i >= 0; i--) {
				OtpMessage message = this.messages.get(i);
				if (message.phoneNumber().equals(phoneNumber)) {
					Matcher matcher = OTP_PATTERN.matcher(message.message());
					if (matcher.find()) {
						return matcher.group(1);
					}
				}
			}
			throw new AssertionError("No captured OTP for " + phoneNumber);
		}
	}

	private record OtpMessage(String phoneNumber, String message) {
	}
}
