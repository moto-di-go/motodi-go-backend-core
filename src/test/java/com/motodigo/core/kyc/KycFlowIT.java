package com.motodigo.core.kyc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.motodigo.core.auth.dto.AuthTokenResponse;
import com.motodigo.core.auth.service.OtpSender;
import com.motodigo.core.kyc.storage.DocumentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "MOTODIGO_DB_INTEGRATION_TESTS", matches = "true")
class KycFlowIT {

	private static final DockerImageName POSTGIS_IMAGE = DockerImageName.parse("postgis/postgis:16-3.4")
			.asCompatibleSubstituteFor("postgres");
	private static final CapturingOtpSender OTP_SENDER = new CapturingOtpSender();
	private static final InMemoryDocumentStorage DOCUMENT_STORAGE = new InMemoryDocumentStorage();

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
		registry.add("motodigo.kyc.checksum-pepper", () -> "test-kyc-checksum-pepper");
	}

	@BeforeEach
	void reset() {
		OTP_SENDER.clear();
		DOCUMENT_STORAGE.clear();
	}

	@Test
	void userUploadsEncryptedDocumentAndAdminReviewIsAudited() {
		String userToken = registerVerifyLoginAndCreateMototaxi("+237699002200", "Secret1234");
		byte[] pdf = "%PDF-1.7\nKYC plaintext document".getBytes(StandardCharsets.UTF_8);

		var upload = upload(userToken, "CNI", pdf);
		assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(upload.getBody()).isNotNull();
		@SuppressWarnings("unchecked")
		Map<String, Object> document = (Map<String, Object>) upload.getBody().get("document");
		String documentId = String.valueOf(document.get("id"));
		String profileId = String.valueOf(document.get("profileId"));

		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM official_documents
				WHERE id = ?::uuid AND status = 'PENDING_REVIEW' AND encrypted_storage_key LIKE 's3://%'
				""", Integer.class, documentId)).isEqualTo(1);
		assertThat(DOCUMENT_STORAGE.onlyStoredContentAsText()).doesNotContain("KYC plaintext document");

		String adminToken = seedAdminAndLogin("+237686000001", "MotodiAdmin123!");
		var adminDocuments = exchange(adminToken, HttpMethod.GET, "/api/v1/admin/kyc/" + profileId + "/documents", null,
				Map.class);
		assertThat(adminDocuments.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(adminDocuments.getBody()).containsEntry("profileId", profileId);
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM document_access_audits
				WHERE document_id = ?::uuid AND action = 'VIEW' AND correlation_id = 'corr-kyc-it'
				""", Integer.class, documentId)).isEqualTo(1);

		var forbidden = exchange(userToken, HttpMethod.GET, "/api/v1/admin/kyc/" + profileId + "/documents", null, Map.class);
		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	private String registerVerifyLoginAndCreateMototaxi(String phoneNumber, String password) {
		String localPhone = phoneNumber.substring(4);
		var register = this.restTemplate.postForEntity(url("/api/v1/auth/register"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"fullName", "KYC User",
				"cguAccepted", true), Map.class);
		assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		verifyOtp(localPhone, OTP_SENDER.latestCode(phoneNumber), "REGISTRATION");
		AuthTokenResponse login = login(localPhone, password);
		var profile = exchange(login.accessToken(), HttpMethod.POST, "/api/v1/profiles", Map.of(
				"type", "MOTOTAXI",
				"displayName", "KYC Moto",
				"driver", Map.of("gpsConsent", true),
				"motoTaxi", Map.of("licenseNumber", "LIC-" + UUID.randomUUID(), "operatingZone", "Douala")), Map.class);
		assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return login.accessToken();
	}

	private org.springframework.http.ResponseEntity<Map> upload(String token, String type, byte[] content) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		HttpHeaders fileHeaders = new HttpHeaders();
		fileHeaders.setContentType(MediaType.APPLICATION_PDF);
		fileHeaders.setContentDispositionFormData("file", "document.pdf");

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("type", type);
		body.add("file", new HttpEntity<>(new NamedByteArrayResource(content, "document.pdf"), fileHeaders));
		return this.restTemplate.postForEntity(url("/api/v1/kyc/documents"), new HttpEntity<>(body, headers), Map.class);
	}

	private String seedAdminAndLogin(String phoneNumber, String password) {
		UUID userId = UUID.randomUUID();
		UUID profileId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO users (id, phone_number, password_hash, full_name, cgu_accepted, status, phone_verified_at)
				VALUES (?, ?, ?, 'KYC Admin', true, 'ACTIVE', now())
				ON CONFLICT (phone_number) DO NOTHING
				""", userId, phoneNumber, this.passwordEncoder.encode(password));
		UUID existingUserId = this.jdbcTemplate.queryForObject("SELECT id FROM users WHERE phone_number = ?", UUID.class,
				phoneNumber);
		this.jdbcTemplate.update("""
				INSERT INTO profiles (id, user_id, type, validation_status, display_name, active)
				VALUES (?, ?, 'ADMIN', 'VALIDATED', 'KYC Admin', true)
				ON CONFLICT (user_id, type) DO UPDATE SET active = true, validation_status = 'VALIDATED'
				""", profileId, existingUserId);
		UUID existingProfileId = this.jdbcTemplate.queryForObject("""
				SELECT id FROM profiles WHERE user_id = ? AND type = 'ADMIN'
				""", UUID.class, existingUserId);
		this.jdbcTemplate.update("""
				INSERT INTO admin_profiles (profile_id, admin_role, permissions)
				VALUES (?, 'PLATFORM_ADMIN', ARRAY['KYC_REVIEW_READ', 'KYC_DECIDE']::text[])
				ON CONFLICT (profile_id) DO UPDATE SET permissions = EXCLUDED.permissions
				""", existingProfileId);
		return login(phoneNumber.substring(4), password).accessToken();
	}

	private void verifyOtp(String phoneNumber, String code, String purpose) {
		var response = this.restTemplate.postForEntity(url("/api/v1/auth/otp/verify"), Map.of(
				"phoneNumber", phoneNumber,
				"code", code,
				"purpose", purpose), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Correlation-Id", "corr-kyc-it");
		return this.restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), responseType);
	}

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}

	static class NamedByteArrayResource extends ByteArrayResource {

		private final String filename;

		NamedByteArrayResource(byte[] byteArray, String filename) {
			super(byteArray);
			this.filename = filename;
		}

		@Override
		public String getFilename() {
			return this.filename;
		}
	}

	static class CapturingOtpSender implements OtpSender {

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
					Matcher matcher = Pattern.compile("(\\d{6})").matcher(message.message());
					if (matcher.find()) {
						return matcher.group(1);
					}
				}
			}
			throw new AssertionError("No OTP captured for " + phoneNumber);
		}
	}

	record OtpMessage(String phoneNumber, String message) {
	}

	static class InMemoryDocumentStorage implements DocumentStorage {

		private final Map<String, byte[]> values = new ConcurrentHashMap<>();

		@Override
		public String put(String objectKey, byte[] encryptedContent, String mimeType) {
			String storageKey = "s3://test-bucket/" + objectKey;
			this.values.put(storageKey, encryptedContent);
			return storageKey;
		}

		@Override
		public byte[] get(String encryptedStorageKey) {
			return this.values.get(encryptedStorageKey);
		}

		@Override
		public void delete(String encryptedStorageKey) {
			this.values.remove(encryptedStorageKey);
		}

		void clear() {
			this.values.clear();
		}

		String onlyStoredContentAsText() {
			assertThat(this.values).hasSize(1);
			return new String(this.values.values().iterator().next(), StandardCharsets.ISO_8859_1);
		}
	}

	@TestConfiguration
	static class KycFlowTestConfiguration {

		@Bean
		@Primary
		OtpSender otpSender() {
			return OTP_SENDER;
		}

		@Bean
		@Primary
		DocumentStorage documentStorage() {
			return DOCUMENT_STORAGE;
		}
	}
}
