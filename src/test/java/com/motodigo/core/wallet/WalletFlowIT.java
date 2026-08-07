package com.motodigo.core.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.motodigo.core.auth.dto.AuthTokenResponse;
import com.motodigo.core.auth.service.OtpSender;
import com.motodigo.core.wallet.exception.WalletErrorCode;
import com.motodigo.core.wallet.exception.WalletException;
import com.motodigo.core.wallet.service.WalletService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "MOTODIGO_DB_INTEGRATION_TESTS", matches = "true")
class WalletFlowIT {

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
	private WalletService walletService;

	@Autowired
	private WalletProvisioningRepository walletProvisioningRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

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
	void walletIsAutoProvisionedAndReadableAfterActivation() {
		String token = registerVerifyAndLogin("+237699100100", "Secret1234");

		var wallet = exchange(token, HttpMethod.GET, "/api/v1/wallet", null, Map.class);
		assertThat(wallet.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(wallet.getBody()).isNotNull();
		assertThat(wallet.getBody().get("currency")).isEqualTo("XAF");
		assertThat(wallet.getBody().get("status")).isEqualTo("ACTIVE");
		assertThat(new BigDecimal(String.valueOf(wallet.getBody().get("currentBalance")))).isEqualByComparingTo("0");

		var ledger = exchange(token, HttpMethod.GET, "/api/v1/wallet/ledger", null, Map.class);
		assertThat(ledger.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat((List<?>) ledger.getBody().get("entries")).isEmpty();
	}

	@Test
	void ledgerReflectsPostingsNewestFirstAndBalanceStaysConsistent() {
		String token = registerVerifyAndLogin("+237699100101", "Secret1234");
		UUID walletId = walletIdFor("+237699100101");

		this.walletService.credit(walletId, new BigDecimal("10000.00"), "depot-momo", "dep-1", null);
		this.walletService.debit(walletId, new BigDecimal("3000.00"), "paiement-trajet", "pay-1", null);

		var ledger = exchange(token, HttpMethod.GET, "/api/v1/wallet/ledger", null, Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> entries = (List<Map<String, Object>>) ledger.getBody().get("entries");
		assertThat(entries).hasSize(2);
		assertThat(entries.get(0).get("type")).isEqualTo("DEBIT");
		assertThat(entries.get(1).get("type")).isEqualTo("CREDIT");

		assertThat(currentBalance(walletId)).isEqualByComparingTo("7000.00");
		assertThat(this.walletService.getWallet(userIdFor("+237699100101"))).isNotNull();
	}

	@Test
	void idempotentCreditPostsOnlyOnce() {
		registerVerifyAndLogin("+237699100102", "Secret1234");
		UUID walletId = walletIdFor("+237699100102");

		this.walletService.credit(walletId, new BigDecimal("5000.00"), "depot", "same-key", null);
		this.walletService.credit(walletId, new BigDecimal("5000.00"), "depot", "same-key", null);

		assertThat(ledgerCount(walletId)).isEqualTo(1);
		assertThat(currentBalance(walletId)).isEqualByComparingTo("5000.00");
	}

	@Test
	void concurrentDebitsNeverOverconsumeAndBalanceStaysDerivable() throws Exception {
		registerVerifyAndLogin("+237699100103", "Secret1234");
		UUID walletId = walletIdFor("+237699100103");
		this.walletService.credit(walletId, new BigDecimal("1000.00"), "depot", "seed", null);

		int threads = 20;
		BigDecimal debitAmount = new BigDecimal("100.00");
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch startGate = new CountDownLatch(1);
		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger insufficient = new AtomicInteger();
		List<Throwable> unexpected = new CopyOnWriteArrayList<>();

		for (int i = 0; i < threads; i++) {
			pool.submit(() -> {
				try {
					startGate.await();
					this.walletService.debit(walletId, debitAmount, "concurrent-debit", null, null);
					succeeded.incrementAndGet();
				}
				catch (WalletException ex) {
					if (ex.errorCode() == WalletErrorCode.INSUFFICIENT_BALANCE) {
						insufficient.incrementAndGet();
					}
					else {
						unexpected.add(ex);
					}
				}
				catch (Throwable ex) {
					unexpected.add(ex);
				}
			});
		}
		startGate.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

		assertThat(unexpected).isEmpty();
		// Exactly 10 debits of 100 fit into the 1000 seed; the rest must be refused.
		assertThat(succeeded.get()).isEqualTo(10);
		assertThat(insufficient.get()).isEqualTo(10);
		assertThat(currentBalance(walletId)).isEqualByComparingTo("0.00");
		// AC: le solde derive des mouvements reste coherent.
		BigDecimal derived = this.jdbcTemplate.queryForObject("""
				SELECT COALESCE(SUM(CASE type WHEN 'CREDIT' THEN amount WHEN 'DEBIT' THEN -amount ELSE 0 END), 0)
				FROM ledger_entries WHERE wallet_id = ?
				""", BigDecimal.class, walletId);
		assertThat(currentBalance(walletId)).isEqualByComparingTo(derived);
	}

	@Test
	void existingActiveAccountWithoutWalletIsProvisionedOnLoginAndGet() {
		String phoneNumber = "+237699100104";
		registerVerifyAndLogin(phoneNumber, "Secret1234");
		UUID userId = userIdFor(phoneNumber);

		// Simulate a legacy ACTIVE account created before wallet provisioning existed.
		this.jdbcTemplate.update("DELETE FROM wallets WHERE user_id = ?", userId);
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM wallets WHERE user_id = ?", Integer.class, userId))
				.isZero();

		var login = this.restTemplate.postForEntity(url("/api/v1/auth/login"), Map.of(
				"phoneNumber", phoneNumber.substring(4),
				"password", "Secret1234",
				"deviceId", "wallet-it-legacy"), AuthTokenResponse.class);
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(login.getBody()).isNotNull();
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM wallets WHERE user_id = ?", Integer.class, userId))
				.isEqualTo(1);

		this.jdbcTemplate.update("DELETE FROM wallets WHERE user_id = ?", userId);
		var wallet = exchange(login.getBody().accessToken(), HttpMethod.GET, "/api/v1/wallet", null, Map.class);
		assertThat(wallet.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(wallet.getBody()).isNotNull();
		assertThat(wallet.getBody().get("currency")).isEqualTo("XAF");
		assertThat(wallet.getBody().get("status")).isEqualTo("ACTIVE");
		assertThat(new BigDecimal(String.valueOf(wallet.getBody().get("currentBalance")))).isEqualByComparingTo("0");
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM wallets WHERE user_id = ?", Integer.class, userId))
				.isEqualTo(1);
	}

	@Test
	void ensureWalletIsIdempotentAndUniquePerAccount() {
		registerVerifyAndLogin("+237699100105", "Secret1234");
		UUID userId = userIdFor("+237699100105");

		this.walletProvisioningRepository.createActiveWalletIfMissing(userId);
		this.walletProvisioningRepository.createActiveWalletIfMissing(userId);

		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM wallets WHERE user_id = ?", Integer.class, userId))
				.isEqualTo(1);

		// Wallet is account-scoped: additional profiles must not create additional wallets.
		this.jdbcTemplate.update("""
				INSERT INTO profiles (user_id, type, display_name, validation_status, active)
				VALUES (?, 'PASSENGER', 'Passager', 'VALIDATED', true),
				       (?, 'MOTOTAXI', 'Conducteur', 'VALIDATED', false)
				""", userId, userId);
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM wallets WHERE user_id = ?", Integer.class, userId))
				.isEqualTo(1);
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM profiles WHERE user_id = ?", Integer.class, userId))
				.isEqualTo(2);
	}

	@Test
	void transactionalRollbackUndoesLedgerAndBalance() {
		registerVerifyAndLogin("+237699100106", "Secret1234");
		UUID walletId = walletIdFor("+237699100106");
		this.walletService.credit(walletId, new BigDecimal("1000.00"), "seed", "rb-seed", null);

		TransactionTemplate tx = new TransactionTemplate(this.transactionManager);
		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			this.walletService.credit(walletId, new BigDecimal("500.00"), "will-rollback", "rb-1", null);
			throw new IllegalStateException("force rollback");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(ledgerCount(walletId)).isEqualTo(1);
		assertThat(currentBalance(walletId)).isEqualByComparingTo("1000.00");
	}

	private String registerVerifyAndLogin(String phoneNumber, String password) {
		String localPhone = phoneNumber.substring(4);
		var register = this.restTemplate.postForEntity(url("/api/v1/auth/register"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"fullName", "Wallet User",
				"cguAccepted", true), Map.class);
		assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		var verify = this.restTemplate.postForEntity(url("/api/v1/auth/otp/verify"), Map.of(
				"phoneNumber", localPhone,
				"code", OTP_SENDER.latestCode(phoneNumber),
				"purpose", "REGISTRATION"), Map.class);
		assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
		var login = this.restTemplate.postForEntity(url("/api/v1/auth/login"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"deviceId", "wallet-it"), AuthTokenResponse.class);
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(login.getBody()).isNotNull();
		return login.getBody().accessToken();
	}

	private UUID walletIdFor(String phoneNumber) {
		return this.jdbcTemplate.queryForObject("""
				SELECT w.id FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.phone_number = ?
				""", UUID.class, phoneNumber);
	}

	private UUID userIdFor(String phoneNumber) {
		return this.jdbcTemplate.queryForObject("SELECT id FROM users WHERE phone_number = ?", UUID.class, phoneNumber);
	}

	private BigDecimal currentBalance(UUID walletId) {
		return this.jdbcTemplate.queryForObject("SELECT current_balance FROM wallets WHERE id = ?", BigDecimal.class,
				walletId);
	}

	private int ledgerCount(UUID walletId) {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM ledger_entries WHERE wallet_id = ?", Integer.class,
				walletId);
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

	static class CapturingOtpSender implements OtpSender {

		private final List<OtpMessage> messages = new CopyOnWriteArrayList<>();

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

	@TestConfiguration
	static class WalletFlowTestConfiguration {

		@Bean
		@Primary
		OtpSender otpSender() {
			return OTP_SENDER;
		}
	}
}
