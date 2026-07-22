package com.motodigo.core.kyc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import com.motodigo.core.auth.dto.AuthTokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
@EnabledIfEnvironmentVariable(named = "MOTODIGO_DB_INTEGRATION_TESTS", matches = "true")
class AdminKycFlowIT {

	private static final DockerImageName POSTGIS_IMAGE = DockerImageName.parse("postgis/postgis:16-3.4")
			.asCompatibleSubstituteFor("postgres");

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
	private PasswordEncoder passwordEncoder;

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
	void adminListsPendingProfileAndDecidesKyc() {
		UUID userId = UUID.randomUUID();
		UUID profileId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		seedUser(userId, "+237699002201", "Secret1234");
		seedMototaxiProfile(userId, profileId);
		seedPendingDocument(profileId, documentId);
		String adminToken = seedAdminAndLogin("+237686000001", "MotodiAdmin123!");

		var pending = exchange(adminToken, HttpMethod.GET, "/api/v1/admin/kyc/pending", null, Object.class);
		assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.OK);

		var decision = exchange(adminToken, HttpMethod.POST, "/api/v1/admin/kyc/" + profileId + "/decision",
				Map.of("decision", "VALIDATE"), Map.class);
		assertThat(decision.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(decision.getBody()).containsEntry("profileValidationStatus", "VALIDATED");

		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT validation_status FROM profiles WHERE id = ?::uuid
				""", String.class, profileId)).isEqualTo("VALIDATED");
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT status FROM official_documents WHERE id = ?::uuid
				""", String.class, documentId)).isEqualTo("ACCEPTED");
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM validation_decisions WHERE profile_id = ?::uuid AND decision = 'VALIDATE'
				""", Integer.class, profileId)).isEqualTo(1);
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM notifications WHERE user_id = ?::uuid AND type = 'KYC_DECISION'
				""", Integer.class, userId)).isEqualTo(1);
	}

	private void seedUser(UUID userId, String phoneNumber, String password) {
		this.jdbcTemplate.update("""
				INSERT INTO users (id, phone_number, password_hash, full_name, cgu_accepted, status, phone_verified_at)
				VALUES (?, ?, ?, 'KYC User', true, 'ACTIVE', now())
				""", userId, phoneNumber, this.passwordEncoder.encode(password));
	}

	private void seedMototaxiProfile(UUID userId, UUID profileId) {
		this.jdbcTemplate.update("""
				INSERT INTO profiles (id, user_id, type, validation_status, display_name, active)
				VALUES (?, ?, 'MOTOTAXI', 'PENDING', 'KYC Moto', true)
				""", profileId, userId);
		this.jdbcTemplate.update("""
				INSERT INTO driver_profiles (profile_id, gps_consent)
				VALUES (?, true)
				""", profileId);
		this.jdbcTemplate.update("""
				INSERT INTO moto_taxi_profiles (profile_id, license_number, operating_zone)
				VALUES (?, ?, 'Douala')
				""", profileId, "LIC-" + UUID.randomUUID());
	}

	private void seedPendingDocument(UUID profileId, UUID documentId) {
		this.jdbcTemplate.update("""
				INSERT INTO official_documents (
				  id, profile_id, type, status, encrypted_storage_key, mime_type, size_bytes, checksum
				)
				VALUES (?, ?, 'CNI', 'PENDING_REVIEW', 's3://bucket/doc.enc', 'application/pdf', 16, ?)
				""", documentId, profileId, "checksum-" + documentId);
	}

	private String seedAdminAndLogin(String phoneNumber, String password) {
		UUID userId = UUID.randomUUID();
		UUID profileId = UUID.randomUUID();
		seedUser(userId, phoneNumber, password);
		this.jdbcTemplate.update("""
				INSERT INTO profiles (id, user_id, type, validation_status, display_name, active)
				VALUES (?, ?, 'ADMIN', 'VALIDATED', 'KYC Admin', true)
				""", profileId, userId);
		this.jdbcTemplate.update("""
				INSERT INTO admin_profiles (profile_id, admin_role, permissions)
				VALUES (?, 'PLATFORM_ADMIN', ARRAY['KYC_REVIEW_READ', 'KYC_DECIDE']::text[])
				""", profileId);
		return login(phoneNumber.substring(4), password).accessToken();
	}

	private AuthTokenResponse login(String phoneNumber, String password) {
		var response = this.restTemplate.postForEntity(url("/api/v1/auth/login"), Map.of(
				"phoneNumber", phoneNumber,
				"password", password,
				"deviceId", "kyc-it"), AuthTokenResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		return response.getBody();
	}

	private <T> org.springframework.http.ResponseEntity<T> exchange(String accessToken, HttpMethod method, String path,
			Object body, Class<T> responseType) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		headers.set("X-Correlation-Id", "corr-kyc-it");
		return this.restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), responseType);
	}

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}
}
