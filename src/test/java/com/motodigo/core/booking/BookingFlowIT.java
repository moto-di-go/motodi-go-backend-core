package com.motodigo.core.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.motodigo.core.auth.dto.AuthTokenResponse;
import com.motodigo.core.auth.service.OtpSender;
import com.motodigo.core.booking.service.BookingService;
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
class BookingFlowIT {

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
	private BookingService bookingService;

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
	void subscriptionConfirmCancelAndVisibilityCoverAcceptanceCriteria() {
		AuthTokenResponse transporter = registerTransporterValidated("+237699300300", "Secret1234");
		UUID vehicleId = insertValidatedVehicle(transporter.activeProfileId(), "BUS-BOOK-001", 2);
		UUID announcementId = createAnnouncement(transporter.accessToken(), vehicleId, 2);

		AuthTokenResponse passenger = registerPassenger("+237699300301", "Secret1234");
		var created = exchange(passenger.accessToken(), HttpMethod.POST,
				"/api/v1/intercity/announcements/" + announcementId + "/bookings",
				Map.of("seatsRequested", 1, "contactPhone", "+237699300399"), Map.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody()).isNotNull();
		assertThat(created.getBody().get("status")).isEqualTo("PENDING");
		UUID bookingId = UUID.fromString(String.valueOf(created.getBody().get("id")));

		var passengerView = exchange(passenger.accessToken(), HttpMethod.GET, "/api/v1/bookings/" + bookingId, null,
				Map.class);
		assertThat(passengerView.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(passengerView.getBody().get("status")).isEqualTo("PENDING");

		var ownerList = exchange(transporter.accessToken(), HttpMethod.GET,
				"/api/v1/intercity/announcements/" + announcementId + "/bookings", null, Map.class);
		assertThat(ownerList.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(((List<?>) ownerList.getBody().get("items"))).isNotEmpty();

		var mine = exchange(passenger.accessToken(), HttpMethod.GET, "/api/v1/bookings/mine", null, Map.class);
		assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(((List<?>) mine.getBody().get("items"))).isNotEmpty();

		var confirmed = exchange(transporter.accessToken(), HttpMethod.POST,
				"/api/v1/bookings/" + bookingId + "/confirm", null, Map.class);
		assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(confirmed.getBody().get("status")).isEqualTo("PENDING_PAYMENT");

		Integer remaining = this.jdbcTemplate.queryForObject(
				"SELECT remaining_seats FROM intercity_announcements WHERE id = ?", Integer.class, announcementId);
		assertThat(remaining).isEqualTo(1);

		Boolean lockExists = this.jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM seat_locks WHERE booking_id = ? AND consumed = false)", Boolean.class,
				bookingId);
		assertThat(lockExists).isTrue();

		var cancelled = exchange(passenger.accessToken(), HttpMethod.POST, "/api/v1/bookings/" + bookingId + "/cancel",
				Map.of("cancellationReason", "Changement de plan"), Map.class);
		assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(cancelled.getBody().get("status")).isEqualTo("CANCELLED");

		remaining = this.jdbcTemplate.queryForObject(
				"SELECT remaining_seats FROM intercity_announcements WHERE id = ?", Integer.class, announcementId);
		assertThat(remaining).isEqualTo(2);
	}

	@Test
	void subscriptionImpossibleWhenFull() {
		AuthTokenResponse transporter = registerTransporterValidated("+237699300310", "Secret1234");
		UUID vehicleId = insertValidatedVehicle(transporter.activeProfileId(), "BUS-BOOK-002", 1);
		UUID announcementId = createAnnouncement(transporter.accessToken(), vehicleId, 1);
		this.jdbcTemplate.update("UPDATE intercity_announcements SET remaining_seats = 0, status = 'FULL' WHERE id = ?",
				announcementId);

		AuthTokenResponse passenger = registerPassenger("+237699300311", "Secret1234");
		var refused = exchange(passenger.accessToken(), HttpMethod.POST,
				"/api/v1/intercity/announcements/" + announcementId + "/bookings",
				Map.of("seatsRequested", 1, "contactPhone", "+237699300399"), Map.class);
		assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(refused.getBody().get("errorCode")).isEqualTo("BOOKING_NOT_AVAILABLE");
	}

	@Test
	void concurrentConfirmsDoNotOverconsumeSeats() throws Exception {
		AuthTokenResponse transporter = registerTransporterValidated("+237699300320", "Secret1234");
		UUID vehicleId = insertValidatedVehicle(transporter.activeProfileId(), "BUS-BOOK-003", 1);
		UUID announcementId = createAnnouncement(transporter.accessToken(), vehicleId, 1);

		AuthTokenResponse passenger1 = registerPassenger("+237699300321", "Secret1234");
		AuthTokenResponse passenger2 = registerPassenger("+237699300322", "Secret1234");

		UUID booking1 = createPendingBooking(passenger1.accessToken(), announcementId);
		UUID booking2 = createPendingBooking(passenger2.accessToken(), announcementId);

		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger success = new AtomicInteger();
		AtomicInteger conflict = new AtomicInteger();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<?> f1 = pool.submit(() -> confirmWithLatch(transporter.accessToken(), booking1, start, success,
					conflict));
			Future<?> f2 = pool.submit(() -> confirmWithLatch(transporter.accessToken(), booking2, start, success,
					conflict));
			start.countDown();
			f1.get(30, TimeUnit.SECONDS);
			f2.get(30, TimeUnit.SECONDS);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(success.get()).isEqualTo(1);
		assertThat(conflict.get()).isEqualTo(1);

		Integer remaining = this.jdbcTemplate.queryForObject(
				"SELECT remaining_seats FROM intercity_announcements WHERE id = ?", Integer.class, announcementId);
		assertThat(remaining).isEqualTo(0);

		Integer activeLocks = this.jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM seat_locks
				WHERE announcement_id = ? AND consumed = false
				""", Integer.class, announcementId);
		assertThat(activeLocks).isEqualTo(1);
	}

	@Test
	void expirationReleasesSeatsForPendingPayment() {
		AuthTokenResponse transporter = registerTransporterValidated("+237699300330", "Secret1234");
		UUID vehicleId = insertValidatedVehicle(transporter.activeProfileId(), "BUS-BOOK-004", 2);
		UUID announcementId = createAnnouncement(transporter.accessToken(), vehicleId, 2);
		AuthTokenResponse passenger = registerPassenger("+237699300331", "Secret1234");
		UUID bookingId = createPendingBooking(passenger.accessToken(), announcementId);

		var confirmed = exchange(transporter.accessToken(), HttpMethod.POST,
				"/api/v1/bookings/" + bookingId + "/confirm", null, Map.class);
		assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);

		this.jdbcTemplate.update("UPDATE bookings SET expires_at = now() - interval '1 minute' WHERE id = ?",
				bookingId);
		this.jdbcTemplate.update("UPDATE seat_locks SET expires_at = now() - interval '1 minute' WHERE booking_id = ?",
				bookingId);

		int expired = this.bookingService.expireUnpaidBookings();
		assertThat(expired).isGreaterThanOrEqualTo(1);

		String status = this.jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class,
				bookingId);
		assertThat(status).isEqualTo("EXPIRED");

		Integer remaining = this.jdbcTemplate.queryForObject(
				"SELECT remaining_seats FROM intercity_announcements WHERE id = ?", Integer.class, announcementId);
		assertThat(remaining).isEqualTo(2);
	}

	private void confirmWithLatch(String token, UUID bookingId, CountDownLatch start, AtomicInteger success,
			AtomicInteger conflict) {
		try {
			start.await(10, TimeUnit.SECONDS);
			var response = exchange(token, HttpMethod.POST, "/api/v1/bookings/" + bookingId + "/confirm", null,
					Map.class);
			if (response.getStatusCode() == HttpStatus.OK) {
				success.incrementAndGet();
			}
			else if (response.getStatusCode() == HttpStatus.CONFLICT) {
				conflict.incrementAndGet();
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private UUID createPendingBooking(String accessToken, UUID announcementId) {
		var created = exchange(accessToken, HttpMethod.POST,
				"/api/v1/intercity/announcements/" + announcementId + "/bookings",
				Map.of("seatsRequested", 1, "contactPhone", "+237699300399"), Map.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return UUID.fromString(String.valueOf(created.getBody().get("id")));
	}

	private UUID createAnnouncement(String accessToken, UUID vehicleId, int seats) {
		Map<String, Object> body = new HashMap<>();
		body.put("departureCity", "Douala");
		body.put("destinationCity", "Yaounde");
		body.put("departureQuarter", "Bonanjo");
		body.put("departureDate", LocalDate.now().plusDays(5).toString());
		body.put("departureTime", "08:30:00");
		body.put("priceFcfa", 9000);
		body.put("totalSeats", seats);
		body.put("vehicleId", vehicleId.toString());
		body.put("instructions", "Bagages 20kg");
		var created = exchange(accessToken, HttpMethod.POST, "/api/v1/intercity/announcements", body, Map.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return UUID.fromString(String.valueOf(created.getBody().get("id")));
	}

	private AuthTokenResponse registerTransporterValidated(String phoneNumber, String password) {
		String localPhone = phoneNumber.substring(4);
		var register = this.restTemplate.postForEntity(url("/api/v1/auth/register"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"fullName", "Transporteur Booking",
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
				"displayName", "Transporteur Booking",
				"transporter", Map.of("permitNumber", "PERMIT-" + UUID.randomUUID(), "companyName", "Go Bus")),
				Map.class);
		assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		AuthTokenResponse withProfile = login(localPhone, password);
		this.jdbcTemplate.update("UPDATE profiles SET validation_status = 'VALIDATED' WHERE id = ?",
				withProfile.activeProfileId());
		return login(localPhone, password);
	}

	private AuthTokenResponse registerPassenger(String phoneNumber, String password) {
		String localPhone = phoneNumber.substring(4);
		var register = this.restTemplate.postForEntity(url("/api/v1/auth/register"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"fullName", "Passenger Booking",
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
				"displayName", "Voyageur Booking"), Map.class);
		assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return login(localPhone, password);
	}

	private AuthTokenResponse login(String localPhone, String password) {
		var login = this.restTemplate.postForEntity(url("/api/v1/auth/login"), Map.of(
				"phoneNumber", localPhone,
				"password", password,
				"deviceId", "booking-it"), AuthTokenResponse.class);
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(login.getBody()).isNotNull();
		assertThat(login.getBody().activeProfileId()).isNotNull();
		return login.getBody();
	}

	private UUID insertValidatedVehicle(UUID ownerProfileId, String plate, int capacity) {
		return this.jdbcTemplate.queryForObject("""
				INSERT INTO vehicles (owner_profile_id, type, plate_number, capacity, validation_status, active)
				VALUES (?, 'BUS', ?, ?, 'VALIDATED', true)
				RETURNING id
				""", UUID.class, ownerProfileId, plate, capacity);
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
	static class BookingFlowTestConfiguration {

		@Bean
		@Primary
		OtpSender otpSender() {
			return OTP_SENDER;
		}
	}
}
