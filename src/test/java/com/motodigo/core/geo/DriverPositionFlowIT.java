package com.motodigo.core.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.motodigo.core.auth.dto.AuthTokenResponse;
import com.motodigo.core.auth.service.OtpSender;
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
class DriverPositionFlowIT {

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

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("motodigo.auth.jwt-secret", () -> "test-jwt-secret-at-least-32-bytes-long");
		registry.add("motodigo.otp.pepper", () -> "test-otp-pepper-at-least-16-bytes");
		registry.add("motodigo.otp.provider", () -> "test");
	}

	@Test
	void validatedDriverCanPublishPositionAndIsRateLimited() {
		AuthTokenResponse tokens = registerValidatedMototaxi("+237699300300", "Secret1234");

		var offline = exchange(tokens.accessToken(), HttpMethod.PUT, "/api/v1/drivers/position",
				Map.of("lat", 3.87, "lon", 11.52, "consentConfirmed", true), Map.class);
		assertThat(offline.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(offline.getBody()).containsEntry("errorCode", "DRIVER_OFFLINE");

		var availability = exchange(tokens.accessToken(), HttpMethod.PATCH, "/api/v1/drivers/availability",
				Map.of("availability", "AVAILABLE", "gpsConsent", true), Map.class);
		assertThat(availability.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(availability.getBody()).containsEntry("availability", "AVAILABLE");

		var accepted = exchange(tokens.accessToken(), HttpMethod.PUT, "/api/v1/drivers/position",
				Map.of("lat", 3.868, "lon", 11.516, "speedKmh", 22.5, "accuracyMeters", 7.0, "consentConfirmed", true),
				Map.class);
		assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(accepted.getBody()).containsKeys("id", "lat", "lon", "capturedAt");

		var tooSoon = exchange(tokens.accessToken(), HttpMethod.PUT, "/api/v1/drivers/position",
				Map.of("lat", 3.869, "lon", 11.517, "consentConfirmed", true), Map.class);
		assertThat(tooSoon.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(tooSoon.getBody()).containsEntry("errorCode", "POSITION_TOO_FREQUENT");

		var last = exchange(tokens.accessToken(), HttpMethod.GET, "/api/v1/drivers/position", null, Map.class);
		assertThat(last.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(last.getBody()).containsEntry("lat", 3.868);

		Integer lastRows = this.jdbcTemplate.queryForObject("SELECT count(*) FROM driver_last_positions", Integer.class);
		assertThat(lastRows).isEqualTo(1);
	}

	@Test
	void pendingDriverCannotPublishPosition() {
		AuthTokenResponse tokens = registerMototaxi("+237699300301", "Secret1234", false);
		exchange(tokens.accessToken(), HttpMethod.PATCH, "/api/v1/drivers/availability",
				Map.of("availability", "AVAILABLE", "gpsConsent", true), Map.class);
		var refused = exchange(tokens.accessToken(), HttpMethod.PUT, "/api/v1/drivers/position",
				Map.of("lat", 3.87, "lon", 11.52, "consentConfirmed", true), Map.class);
		assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(refused.getBody()).containsEntry("errorCode", "DRIVER_NOT_ALLOWED");
	}

	private AuthTokenResponse registerValidatedMototaxi(String phoneNumber, String password) {
		return registerMototaxi(phoneNumber, password, true);
	}

	private AuthTokenResponse registerMototaxi(String phoneNumber, String password, boolean validated) {
		String localPhone = phoneNumber.substring(4);
		var register = this.restTemplate.postForEntity(url("/api/v1/auth/register"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"fullName", "Conducteur IT",
				"cguAccepted", true), Map.class);
		assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		this.restTemplate.postForEntity(url("/api/v1/auth/otp/verify"), Map.of(
				"phoneNumber", localPhone,
				"code", OTP_SENDER.latestCode(phoneNumber),
				"purpose", "REGISTRATION"), Map.class);
		AuthTokenResponse login = login(localPhone, password);
		var profile = exchange(login.accessToken(), HttpMethod.POST, "/api/v1/profiles", Map.of(
				"type", "MOTOTAXI",
				"displayName", "Moto IT",
				"driver", Map.of("gpsConsent", true),
				"motoTaxi", Map.of("licenseNumber", "LIC-" + UUID.randomUUID(), "operatingZone", "Yaounde")),
				Map.class);
		assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		if (validated) {
			this.jdbcTemplate.update("UPDATE profiles SET validation_status = 'VALIDATED' WHERE id = ?",
					UUID.fromString(String.valueOf(profile.getBody().get("id"))));
		}
		return login(localPhone, password);
	}

	private AuthTokenResponse login(String localPhone, String password) {
		var login = this.restTemplate.postForEntity(url("/api/v1/auth/login"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"deviceId", "geo-it"), AuthTokenResponse.class);
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
		return login.getBody();
	}

	private <T> org.springframework.http.ResponseEntity<T> exchange(String accessToken, HttpMethod method, String path,
			Object body, Class<T> responseType) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return this.restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), responseType);
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
