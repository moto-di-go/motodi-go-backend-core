package com.motodigo.core.intercity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
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
import org.springframework.http.ResponseEntity;
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
class IntercityAnnouncementFlowIT {

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
	void createSearchUpdateCloseAndWideningCoverAcceptanceCriteria() {
		AuthTokenResponse transporter = registerTransporterValidated("+237699200200", "Secret1234");
		UUID vehicleId = insertValidatedVehicle(transporter.activeProfileId(), "BUS-IT-001", 14);

		Map<String, Object> createBody = new HashMap<>();
		createBody.put("departureCity", "Douala");
		createBody.put("destinationCity", "Yaoundé");
		createBody.put("ptRamassage", "Bonanjo");
		createBody.put("departureDate", LocalDate.now().plusDays(5).toString());
		createBody.put("departureTime", "08:30:00");
		createBody.put("priceFcfa", 9000);
		createBody.put("totalSeats", 14);
		createBody.put("vehicleId", vehicleId.toString());
		createBody.put("instructions", "Bagages 20kg");

		var created = exchange(transporter.accessToken(), HttpMethod.POST, "/api/v1/intercity/announcements",
				createBody, Map.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody()).isNotNull();
		assertThat(created.getBody().get("status")).isEqualTo("PUBLISHED");
		assertThat(created.getBody().get("remainingSeats")).isEqualTo(14);
		assertThat(created.getBody().get("totalSeats")).isEqualTo(14);
		UUID announcementId = UUID.fromString(String.valueOf(created.getBody().get("id")));

		// Incomplete / unauthorized create
		AuthTokenResponse pending = registerTransporterPending("+237699200201", "Secret1234");
		UUID pendingVehicle = insertValidatedVehicle(pending.activeProfileId(), "BUS-IT-002", 10);
		Map<String, Object> pendingCreate = new HashMap<>(createBody);
		pendingCreate.put("vehicleId", pendingVehicle.toString());
		var forbidden = exchange(pending.accessToken(), HttpMethod.POST, "/api/v1/intercity/announcements", pendingCreate,
				Map.class);
		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		Map<String, Object> incomplete = Map.of("departureCity", "Douala");
		var badRequest = exchange(transporter.accessToken(), HttpMethod.POST, "/api/v1/intercity/announcements",
				incomplete, Map.class);
		assertThat(badRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		AuthTokenResponse passenger = registerPassenger("+237699200202", "Secret1234");
		String searchPath = "/api/v1/intercity/announcements?departureCity=Douala&destinationCity=Yaounde&date="
				+ LocalDate.now().plusDays(5)
				+ "&maxPrice=10000&timeFrom=08:00:00&timeTo=09:00:00&ptRamassage=Bonanjo&onlyAvailable=true&sort=price";
		var search = exchange(passenger.accessToken(), HttpMethod.GET, searchPath, null, Map.class);
		assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) search.getBody().get("items");
		assertThat(items).hasSize(1);
		assertThat(items.get(0).get("remainingSeats")).isEqualTo(14);
		assertThat(items.get(0).get("id")).isEqualTo(announcementId.toString());
		// Accent-insensitive: query "Yaounde" matches stored "Yaoundé".
		assertThat(items.get(0).get("destinationCity")).isEqualTo("Yaoundé");

		var emptySearch = exchange(passenger.accessToken(), HttpMethod.GET,
				"/api/v1/intercity/announcements?departureCity=Douala&destinationCity=Yaounde&date="
						+ LocalDate.now().plusDays(40) + "&maxPrice=1000&ptRamassage=Akwa&onlyAvailable=true",
				null, Map.class);
		assertThat(emptySearch.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat((List<?>) emptySearch.getBody().get("items")).isEmpty();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> suggestions = (List<Map<String, Object>>) emptySearch.getBody()
				.get("wideningSuggestions");
		assertThat(suggestions).isNotEmpty();
		assertThat(suggestions).extracting(s -> s.get("code"))
				.contains("RELAX_DATE", "RAISE_MAX_PRICE", "DROP_PT_RAMASSAGE", "INCLUDE_FULL");

		var mine = exchange(transporter.accessToken(), HttpMethod.GET, "/api/v1/intercity/announcements/mine", null,
				Map.class);
		assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat((List<?>) mine.getBody().get("items")).hasSize(1);

		Map<String, Object> patch = Map.of("priceFcfa", 8500, "instructions", "Bagages 15kg");
		var updated = exchange(transporter.accessToken(), HttpMethod.PATCH,
				"/api/v1/intercity/announcements/" + announcementId, patch, Map.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(new BigDecimal(String.valueOf(updated.getBody().get("priceFcfa"))))
				.isEqualByComparingTo("8500");

		UUID passengerProfileId = transporterPassengerForBooking(passenger);
		this.jdbcTemplate.update("""
				INSERT INTO bookings (announcement_id, passenger_profile_id, status, seats_requested)
				VALUES (?, ?, 'PENDING', 1)
				""", announcementId, passengerProfileId);
		var blockedUpdate = exchange(transporter.accessToken(), HttpMethod.PATCH,
				"/api/v1/intercity/announcements/" + announcementId, Map.of("priceFcfa", 8000), Map.class);
		assertThat(blockedUpdate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		this.jdbcTemplate.update("DELETE FROM bookings WHERE announcement_id = ?", announcementId);

		var closed = exchange(transporter.accessToken(), HttpMethod.POST,
				"/api/v1/intercity/announcements/" + announcementId + "/close", null, Map.class);
		assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(closed.getBody().get("status")).isEqualTo("CLOSED");

		var afterCloseUpdate = exchange(transporter.accessToken(), HttpMethod.PATCH,
				"/api/v1/intercity/announcements/" + announcementId, Map.of("priceFcfa", 7000), Map.class);
		assertThat(afterCloseUpdate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void pastDepartureAndInvalidVehicleAreRejected() {
		AuthTokenResponse transporter = registerTransporterValidated("+237699200210", "Secret1234");
		UUID vehicleId = insertValidatedVehicle(transporter.activeProfileId(), "BUS-IT-010", 8);
		UUID foreignVehicle = insertValidatedVehicle(UUID.randomUUID(), "BUS-IT-011", 8);

		Map<String, Object> past = new HashMap<>();
		past.put("departureCity", "Douala");
		past.put("destinationCity", "Bafoussam");
		past.put("departureDate", LocalDate.now().minusDays(1).toString());
		past.put("departureTime", "08:00:00");
		past.put("priceFcfa", 5000);
		past.put("totalSeats", 4);
		past.put("vehicleId", vehicleId.toString());
		var pastResp = exchange(transporter.accessToken(), HttpMethod.POST, "/api/v1/intercity/announcements", past,
				Map.class);
		assertThat(pastResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		Map<String, Object> badVehicle = new HashMap<>(past);
		badVehicle.put("departureDate", LocalDate.now().plusDays(3).toString());
		badVehicle.put("vehicleId", foreignVehicle.toString());
		var vehicleResp = exchange(transporter.accessToken(), HttpMethod.POST, "/api/v1/intercity/announcements",
				badVehicle, Map.class);
		assertThat(vehicleResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private AuthTokenResponse registerTransporterValidated(String phoneNumber, String password) {
		AuthTokenResponse tokens = registerCreateTransporterAndRelogin(phoneNumber, password);
		this.jdbcTemplate.update("UPDATE profiles SET validation_status = 'VALIDATED' WHERE id = ?",
				tokens.activeProfileId());
		return login(phoneNumber.substring(4), password);
	}

	private AuthTokenResponse registerTransporterPending(String phoneNumber, String password) {
		return registerCreateTransporterAndRelogin(phoneNumber, password);
	}

	private AuthTokenResponse registerCreateTransporterAndRelogin(String phoneNumber, String password) {
		String localPhone = phoneNumber.substring(4);
		var register = this.restTemplate.postForEntity(url("/api/v1/auth/register"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"fullName", "Intercity User",
				"cguAccepted", true), Map.class);
		assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		var verify = this.restTemplate.postForEntity(url("/api/v1/auth/otp/verify"), Map.of(
				"phoneNumber", localPhone,
				"code", OTP_SENDER.latestCode(phoneNumber),
				"purpose", "REGISTRATION"), Map.class);
		assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
		AuthTokenResponse login = login(localPhone, password);
		var profile = exchange(login.accessToken(), HttpMethod.POST, "/api/v1/profiles", Map.of(
				"type", "TRANSPORTER",
				"displayName", "Transporteur IT",
				"transporter", Map.of("permitNumber", "PERMIT-" + UUID.randomUUID(), "companyName", "Go Bus")),
				Map.class);
		assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return login(localPhone, password);
	}

	private AuthTokenResponse registerPassenger(String phoneNumber, String password) {
		String localPhone = phoneNumber.substring(4);
		var register = this.restTemplate.postForEntity(url("/api/v1/auth/register"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"fullName", "Passenger IT",
				"cguAccepted", true), Map.class);
		assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		var verify = this.restTemplate.postForEntity(url("/api/v1/auth/otp/verify"), Map.of(
				"phoneNumber", localPhone,
				"code", OTP_SENDER.latestCode(phoneNumber),
				"purpose", "REGISTRATION"), Map.class);
		assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
		AuthTokenResponse login = login(localPhone, password);
		var profile = exchange(login.accessToken(), HttpMethod.POST, "/api/v1/profiles", Map.of(
				"type", "PASSENGER",
				"displayName", "Voyageur IT"), Map.class);
		assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return login(localPhone, password);
	}

	private AuthTokenResponse login(String localPhone, String password) {
		var login = this.restTemplate.postForEntity(url("/api/v1/auth/login"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"deviceId", "intercity-it"), AuthTokenResponse.class);
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(login.getBody()).isNotNull();
		assertThat(login.getBody().activeProfileId()).isNotNull();
		return login.getBody();
	}

	private UUID insertValidatedVehicle(UUID ownerProfileId, String plate, int capacity) {
		// Ensure owner profile row exists when seeding a foreign vehicle for negative tests.
		Boolean exists = this.jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM profiles WHERE id = ?)",
				Boolean.class, ownerProfileId);
		if (!Boolean.TRUE.equals(exists)) {
			UUID userId = UUID.randomUUID();
			String phone = "+23769" + String.format("%08d", Math.floorMod(ownerProfileId.getMostSignificantBits(), 100_000_000L));
			this.jdbcTemplate.update("""
					INSERT INTO users (id, phone_number, password_hash, full_name, cgu_accepted, status, phone_verified_at)
					VALUES (?, ?, 'x', 'Ghost Owner', true, 'ACTIVE', now())
					""", userId, phone);
			this.jdbcTemplate.update("""
					INSERT INTO profiles (id, user_id, type, validation_status, display_name, active)
					VALUES (?, ?, 'TRANSPORTER', 'VALIDATED', 'Ghost', true)
					""", ownerProfileId, userId);
			this.jdbcTemplate.update("""
					INSERT INTO driver_profiles (profile_id, availability, gps_consent)
					VALUES (?, 'OFFLINE', false)
					""", ownerProfileId);
			this.jdbcTemplate.update("""
					INSERT INTO transporter_profiles (profile_id, permit_number, company_name)
					VALUES (?, ?, 'Ghost Co')
					""", ownerProfileId, "GHOST-" + ownerProfileId);
		}
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO vehicles (owner_profile_id, type, plate_number, capacity, validation_status, active)
				VALUES (?, 'BUS', ?, ?, 'VALIDATED', true)
				RETURNING id
				""", UUID.class, ownerProfileId, plate, capacity);
	}

	private UUID transporterPassengerForBooking(AuthTokenResponse passenger) {
		return passenger.activeProfileId();
	}

	private <T> ResponseEntity<T> exchange(String accessToken, HttpMethod method, String path, Object body,
			Class<T> responseType) {
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
	static class IntercityFlowTestConfiguration {

		@Bean
		@Primary
		OtpSender otpSender() {
			return OTP_SENDER;
		}
	}
}
