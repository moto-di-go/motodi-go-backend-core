package com.motodigo.core.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.motodigo.core.auth.dto.AuthTokenResponse;
import com.motodigo.core.auth.service.OtpSender;
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
class PaymentDepositFlowIT {

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
	private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("motodigo.auth.jwt-secret", () -> "test-jwt-secret-at-least-32-bytes-long");
		registry.add("motodigo.otp.pepper", () -> "test-otp-pepper-at-least-16-bytes");
		registry.add("motodigo.otp.provider", () -> "test");
		registry.add("motodigo.payment.mobile-money.mode", () -> "stub");
		registry.add("motodigo.payment.mobile-money.pending-ttl", () -> "30m");
	}

	@Test
	void depositSuccessCreditsWalletExactlyOnceAndIsReconciliable() {
		String token = registerVerifyAndLogin("+237699200100", "Secret1234");
		UUID walletId = walletIdFor("+237699200100");

		var initiate = exchange(token, HttpMethod.POST, "/api/v1/wallet/deposits", Map.of(
				"amount", 7500,
				"currency", "XAF",
				"provider", "MTN_MOMO",
				"phoneNumber", "+237699200100"), Map.class, "dep-idem-1");
		assertThat(initiate.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(initiate.getBody()).isNotNull();
		assertThat(initiate.getBody().get("status")).isEqualTo("PENDING_CALLBACK");
		String externalReference = String.valueOf(initiate.getBody().get("externalReference"));
		UUID depositId = UUID.fromString(String.valueOf(initiate.getBody().get("id")));

		HttpHeaders callbackHeaders = new HttpHeaders();
		callbackHeaders.setContentType(MediaType.APPLICATION_JSON);
		callbackHeaders.set("X-Signature", "sha256=test-signature");
		var callback = this.restTemplate.postForEntity(
				url("/api/v1/integrations/mobile-money/mtn/callback"),
				new HttpEntity<>(Map.of(
						"externalReference", externalReference,
						"status", "SUCCESS"), callbackHeaders),
				Map.class);
		assertThat(callback.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(callback.getBody().get("status")).isEqualTo("CONFIRMED");

		var replay = this.restTemplate.postForEntity(
				url("/api/v1/integrations/mobile-money/mtn/callback"),
				new HttpEntity<>(Map.of(
						"externalReference", externalReference,
						"status", "SUCCESS"), callbackHeaders),
				Map.class);
		assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(replay.getBody().get("status")).isEqualTo("CONFIRMED");

		assertThat(currentBalance(walletId)).isEqualByComparingTo("7500.00");
		assertThat(ledgerCount(walletId)).isEqualTo(1);
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM ledger_entries WHERE wallet_id = ? AND idempotency_key = ?",
				Integer.class, walletId, "deposit:" + depositId)).isEqualTo(1);

		var status = exchange(token, HttpMethod.GET, "/api/v1/wallet/deposits/" + depositId, null, Map.class, null);
		assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(status.getBody().get("status")).isEqualTo("CONFIRMED");
		assertThat(status.getBody().get("externalReference")).isEqualTo(externalReference);

		String adminToken = createAdminAndLogin();
		var adminList = exchange(adminToken, HttpMethod.GET,
				"/api/v1/admin/payments/transactions?externalReference=" + externalReference,
				null, Map.class, null);
		assertThat(adminList.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> transactions = (List<Map<String, Object>>) adminList.getBody().get("transactions");
		assertThat(transactions).hasSize(1);
		assertThat(transactions.getFirst().get("externalReference")).isEqualTo(externalReference);
	}

	@Test
	void failedCallbackIsVisibleToClientWithoutCredit() {
		String token = registerVerifyAndLogin("+237699200101", "Secret1234");
		UUID walletId = walletIdFor("+237699200101");

		var initiate = exchange(token, HttpMethod.POST, "/api/v1/wallet/deposits", Map.of(
				"amount", 3000,
				"currency", "XAF",
				"provider", "ORANGE_MONEY",
				"phoneNumber", "+237699200101"), Map.class, null);
		assertThat(initiate.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		String externalReference = String.valueOf(initiate.getBody().get("externalReference"));
		UUID depositId = UUID.fromString(String.valueOf(initiate.getBody().get("id")));

		var callback = this.restTemplate.postForEntity(
				url("/api/v1/integrations/mobile-money/orange/callback"),
				new HttpEntity<>(Map.of(
						"externalReference", externalReference,
						"status", "FAILED",
						"failureReason", "Fonds insuffisants"), jsonHeaders()),
				Map.class);
		assertThat(callback.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(callback.getBody().get("status")).isEqualTo("FAILED");

		var status = exchange(token, HttpMethod.GET, "/api/v1/wallet/deposits/" + depositId, null, Map.class, null);
		assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(status.getBody().get("status")).isEqualTo("FAILED");
		assertThat(status.getBody().get("failureReason")).isEqualTo("Fonds insuffisants");
		assertThat(currentBalance(walletId)).isEqualByComparingTo("0");
		assertThat(ledgerCount(walletId)).isZero();
	}

	@Test
	void pendingDepositExpiresLazilyOnRead() {
		String token = registerVerifyAndLogin("+237699200102", "Secret1234");
		var initiate = exchange(token, HttpMethod.POST, "/api/v1/wallet/deposits", Map.of(
				"amount", 2000,
				"currency", "XAF",
				"provider", "AGGREGATOR",
				"phoneNumber", "+237699200102"), Map.class, null);
		UUID depositId = UUID.fromString(String.valueOf(initiate.getBody().get("id")));

		this.jdbcTemplate.update("""
				UPDATE payment_transactions
				SET initiated_at = now() - interval '2 hours'
				WHERE id = ?
				""", depositId);

		var status = exchange(token, HttpMethod.GET, "/api/v1/wallet/deposits/" + depositId, null, Map.class, null);
		assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(status.getBody().get("status")).isEqualTo("EXPIRED");
	}

	private String createAdminAndLogin() {
		String phoneNumber = "+237699200199";
		String password = "Secret1234";
		UUID userId = UUID.randomUUID();
		UUID profileId = UUID.randomUUID();
		seedUser(userId, phoneNumber, password);
		this.jdbcTemplate.update("""
				INSERT INTO profiles (id, user_id, type, validation_status, display_name, active)
				VALUES (?, ?, 'ADMIN', 'VALIDATED', 'Pay Admin', true)
				""", profileId, userId);
		this.jdbcTemplate.update("""
				INSERT INTO admin_profiles (profile_id, admin_role, permissions)
				VALUES (?, 'PLATFORM_ADMIN', ARRAY['PAYMENT_RECONCILE_READ']::text[])
				""", profileId);
		return login(phoneNumber.substring(4), password).accessToken();
	}

	private void seedUser(UUID userId, String phoneNumber, String password) {
		this.jdbcTemplate.update("""
				INSERT INTO users (id, phone_number, password_hash, full_name, cgu_accepted, status, phone_verified_at)
				VALUES (?, ?, ?, 'Pay Admin', true, 'ACTIVE', now())
				""", userId, phoneNumber, this.passwordEncoder.encode(password));
		this.jdbcTemplate.update("""
				INSERT INTO wallets (user_id, currency, current_balance, status)
				VALUES (?, 'XAF', 0, 'ACTIVE')
				""", userId);
	}

	private AuthTokenResponse login(String localPhone, String password) {
		var login = this.restTemplate.postForEntity(url("/api/v1/auth/login"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"deviceId", "pay-it-admin"), AuthTokenResponse.class);
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(login.getBody()).isNotNull();
		return login.getBody();
	}

	private String registerVerifyAndLogin(String phoneNumber, String password) {
		String localPhone = phoneNumber.substring(4);
		var register = this.restTemplate.postForEntity(url("/api/v1/auth/register"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"fullName", "Pay User",
				"cguAccepted", true), Map.class);
		assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		var verify = this.restTemplate.postForEntity(url("/api/v1/auth/otp/verify"), Map.of(
				"phoneNumber", localPhone,
				"code", OTP_SENDER.latestCode(phoneNumber),
				"purpose", "REGISTRATION"), Map.class);
		assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
		return login(localPhone, password).accessToken();
	}

	private UUID walletIdFor(String phoneNumber) {
		return this.jdbcTemplate.queryForObject("""
				SELECT w.id FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.phone_number = ?
				""", UUID.class, phoneNumber);
	}

	private BigDecimal currentBalance(UUID walletId) {
		return this.jdbcTemplate.queryForObject("SELECT current_balance FROM wallets WHERE id = ?", BigDecimal.class,
				walletId);
	}

	private int ledgerCount(UUID walletId) {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM ledger_entries WHERE wallet_id = ?", Integer.class,
				walletId);
	}

	private <T> org.springframework.http.ResponseEntity<T> exchange(String token, HttpMethod method, String path,
			Object body, Class<T> type, String idempotencyKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (idempotencyKey != null) {
			headers.set("Idempotency-Key", idempotencyKey);
		}
		return this.restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Signature", "sha256=test");
		return headers;
	}

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}

	@TestConfiguration
	static class OtpTestConfig {
		@Bean
		@Primary
		OtpSender otpSender() {
			return OTP_SENDER;
		}
	}

	static final class CapturingOtpSender implements OtpSender {
		private final CopyOnWriteArrayList<OtpMessage> messages = new CopyOnWriteArrayList<>();

		@Override
		public void sendOtp(String phoneNumber, String message) {
			this.messages.add(new OtpMessage(phoneNumber, message));
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
}
