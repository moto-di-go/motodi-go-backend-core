package com.motodigo.core.profile.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.profile.dto.CreateProfileRequest;
import com.motodigo.core.profile.dto.UpdateProfileRequest;
import com.motodigo.core.profile.entity.DriverAvailability;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ProfileRepository {

	private static final String SELECT_JOINED = """
			SELECT p.*,
			       pp.preferred_language,
			       pp.emergency_contact,
			       dp.availability,
			       dp.gps_consent,
			       dp.last_availability_change_at,
			       mt.license_number,
			       mt.operating_zone,
			       tp.permit_number,
			       tp.company_name AS transporter_company_name,
			       tp.vehicle_id AS transporter_vehicle_id,
			       ap.legal_name,
			       ap.trade_register_number,
			       ap.billing_plan,
			       ap.display_priority,
			       adm.admin_role,
			       adm.permissions
			FROM profiles p
			LEFT JOIN passenger_profiles pp ON pp.profile_id = p.id
			LEFT JOIN driver_profiles dp ON dp.profile_id = p.id
			LEFT JOIN moto_taxi_profiles mt ON mt.profile_id = p.id
			LEFT JOIN transporter_profiles tp ON tp.profile_id = p.id
			LEFT JOIN agency_profiles ap ON ap.profile_id = p.id
			LEFT JOIN admin_profiles adm ON adm.profile_id = p.id
			""";

	private final JdbcTemplate jdbcTemplate;

	public ProfileRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public boolean existsAnyByUserId(UUID userId) {
		Boolean exists = this.jdbcTemplate.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM profiles WHERE user_id = ?)
				""", Boolean.class, userId);
		return Boolean.TRUE.equals(exists);
	}

	public List<ProfileRecord> findByUserId(UUID userId) {
		return this.jdbcTemplate.query(SELECT_JOINED + """
				WHERE p.user_id = ?
				ORDER BY p.created_at ASC
				""", this::mapProfile, userId);
	}

	public Optional<ProfileRecord> findOwnedById(UUID userId, UUID profileId) {
		return this.jdbcTemplate.query(SELECT_JOINED + """
				WHERE p.user_id = ? AND p.id = ?
				""", this::mapProfile, userId, profileId).stream().findFirst();
	}

	public Optional<ProfileRecord> findById(UUID profileId) {
		return this.jdbcTemplate.query(SELECT_JOINED + """
				WHERE p.id = ?
				""", this::mapProfile, profileId).stream().findFirst();
	}

	public Optional<ProfileRecord> findActiveByUserId(UUID userId) {
		return this.jdbcTemplate.query(SELECT_JOINED + """
				WHERE p.user_id = ? AND p.active = true
				""", this::mapProfile, userId).stream().findFirst();
	}

	public ProfileRecord create(UUID userId, CreateProfileRequest request, ValidationStatus validationStatus, boolean active) {
		UUID profileId = this.jdbcTemplate.queryForObject("""
				INSERT INTO profiles (user_id, type, validation_status, display_name, avatar_url, active)
				VALUES (?, ?, ?, ?, ?, ?)
				RETURNING id
				""", UUID.class, userId, request.type().name(), validationStatus.name(), request.displayName(),
				blankToNull(request.avatarUrl()), active);
		insertDetails(profileId, request);
		return findOwnedById(userId, profileId).orElseThrow();
	}

	public ProfileRecord update(UUID userId, ProfileRecord existing, UpdateProfileRequest request) {
		this.jdbcTemplate.update("""
				UPDATE profiles
				SET display_name = COALESCE(?, display_name),
				    avatar_url = COALESCE(?, avatar_url),
				    updated_at = now()
				WHERE id = ? AND user_id = ?
				""", blankToNull(request.displayName()), blankToNull(request.avatarUrl()), existing.id(), userId);
		updateDetails(existing, request);
		return findOwnedById(userId, existing.id()).orElseThrow();
	}

	public void clearActive(UUID userId) {
		this.jdbcTemplate.update("""
				UPDATE profiles
				SET active = false, updated_at = now()
				WHERE user_id = ? AND active = true
				""", userId);
	}

	public void markActive(UUID userId, UUID profileId) {
		this.jdbcTemplate.update("""
				UPDATE profiles
				SET active = true, updated_at = now()
				WHERE user_id = ? AND id = ?
				""", userId, profileId);
	}

	private void insertDetails(UUID profileId, CreateProfileRequest request) {
		switch (request.type()) {
			case PASSENGER -> this.jdbcTemplate.update("""
					INSERT INTO passenger_profiles (profile_id, preferred_language, emergency_contact)
					VALUES (?, COALESCE(?, 'fr'), ?)
					""", profileId,
					request.passenger() == null ? null : blankToNull(request.passenger().preferredLanguage()),
					request.passenger() == null ? null : blankToNull(request.passenger().emergencyContact()));
			case MOTOTAXI -> {
				insertDriver(profileId, request.driver() == null ? null : request.driver().gpsConsent());
				this.jdbcTemplate.update("""
						INSERT INTO moto_taxi_profiles (profile_id, license_number, operating_zone)
						VALUES (?, ?, ?)
						""", profileId,
						request.motoTaxi() == null ? null : blankToNull(request.motoTaxi().licenseNumber()),
						request.motoTaxi() == null ? null : blankToNull(request.motoTaxi().operatingZone()));
			}
			case TRANSPORTER -> {
				insertDriver(profileId, request.driver() == null ? null : request.driver().gpsConsent());
				this.jdbcTemplate.update("""
						INSERT INTO transporter_profiles (profile_id, permit_number, company_name)
						VALUES (?, ?, ?)
						""", profileId,
						request.transporter() == null ? null : blankToNull(request.transporter().permitNumber()),
						request.transporter() == null ? null : blankToNull(request.transporter().companyName()));
			}
			case AGENCY -> this.jdbcTemplate.update("""
					INSERT INTO agency_profiles (profile_id, legal_name, trade_register_number, billing_plan)
					VALUES (?, ?, ?, COALESCE(?, 'STANDARD'))
					""", profileId, blankToNull(request.agency().legalName()),
					blankToNull(request.agency().tradeRegisterNumber()),
					normalizeAgencyBillingPlan(request.agency().billingPlan()));
			case ADMIN -> throw new IllegalArgumentException("ADMIN profile creation is not self-service");
		}
	}

	private void updateDetails(ProfileRecord existing, UpdateProfileRequest request) {
		switch (existing.type()) {
			case PASSENGER -> {
				if (request.passenger() != null) {
					this.jdbcTemplate.update("""
							UPDATE passenger_profiles
							SET preferred_language = COALESCE(?, preferred_language),
							    emergency_contact = COALESCE(?, emergency_contact)
							WHERE profile_id = ?
							""", blankToNull(request.passenger().preferredLanguage()),
							blankToNull(request.passenger().emergencyContact()), existing.id());
				}
			}
			case MOTOTAXI -> {
				updateDriver(existing.id(), request.driver());
				if (request.motoTaxi() != null) {
					this.jdbcTemplate.update("""
							UPDATE moto_taxi_profiles
							SET license_number = COALESCE(?, license_number),
							    operating_zone = COALESCE(?, operating_zone)
							WHERE profile_id = ?
							""", blankToNull(request.motoTaxi().licenseNumber()),
							blankToNull(request.motoTaxi().operatingZone()), existing.id());
				}
			}
			case TRANSPORTER -> {
				updateDriver(existing.id(), request.driver());
				if (request.transporter() != null) {
					this.jdbcTemplate.update("""
							UPDATE transporter_profiles
							SET permit_number = COALESCE(?, permit_number),
							    company_name = COALESCE(?, company_name)
							WHERE profile_id = ?
							""", blankToNull(request.transporter().permitNumber()),
							blankToNull(request.transporter().companyName()), existing.id());
				}
			}
			case AGENCY -> {
				if (request.agency() != null) {
					this.jdbcTemplate.update("""
							UPDATE agency_profiles
							SET legal_name = COALESCE(?, legal_name),
							    trade_register_number = COALESCE(?, trade_register_number),
							    billing_plan = COALESCE(?, billing_plan)
							WHERE profile_id = ?
							""", blankToNull(request.agency().legalName()),
							blankToNull(request.agency().tradeRegisterNumber()),
							normalizeAgencyBillingPlan(request.agency().billingPlan()),
							existing.id());
				}
			}
			case ADMIN -> {
			}
		}
	}

	public void updateAvailability(UUID profileId, DriverAvailability availability, boolean gpsConsent) {
		this.jdbcTemplate.update("""
				UPDATE driver_profiles
				SET availability = ?,
				    gps_consent = ?,
				    last_availability_change_at = now()
				WHERE profile_id = ?
				""", availability.name(), gpsConsent, profileId);
	}

	private void insertDriver(UUID profileId, Boolean gpsConsent) {
		this.jdbcTemplate.update("""
				INSERT INTO driver_profiles (profile_id, gps_consent)
				VALUES (?, COALESCE(?, false))
				""", profileId, gpsConsent);
	}

	private void updateDriver(UUID profileId, com.motodigo.core.profile.dto.ProfileDetailsRequests.DriverDetailsRequest request) {
		if (request == null || request.gpsConsent() == null) {
			return;
		}
		this.jdbcTemplate.update("""
				UPDATE driver_profiles
				SET gps_consent = ?
				WHERE profile_id = ?
				""", request.gpsConsent(), profileId);
	}

	private ProfileRecord mapProfile(ResultSet rs, int rowNum) throws SQLException {
		ProfileType type = ProfileType.valueOf(rs.getString("type"));
		return new ProfileRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				type,
				ValidationStatus.valueOf(rs.getString("validation_status")),
				rs.getString("display_name"),
				rs.getString("avatar_url"),
				rs.getBigDecimal("average_rating"),
				rs.getBoolean("active"),
				instant(rs, "created_at"),
				instant(rs, "updated_at"),
				type == ProfileType.PASSENGER ? new ProfileRecord.PassengerDetails(
						rs.getString("preferred_language"),
						rs.getString("emergency_contact")) : null,
				isDriver(type) ? new ProfileRecord.DriverDetails(
						DriverAvailability.valueOf(rs.getString("availability")),
						rs.getBoolean("gps_consent"),
						instant(rs, "last_availability_change_at")) : null,
				type == ProfileType.MOTOTAXI ? new ProfileRecord.MotoTaxiDetails(
						rs.getString("license_number"),
						rs.getString("operating_zone")) : null,
				type == ProfileType.TRANSPORTER ? new ProfileRecord.TransporterDetails(
						rs.getString("permit_number"),
						rs.getString("transporter_company_name"),
						rs.getObject("transporter_vehicle_id", UUID.class)) : null,
				type == ProfileType.AGENCY ? new ProfileRecord.AgencyDetails(
						rs.getString("legal_name"),
						rs.getString("trade_register_number"),
						rs.getString("billing_plan"),
						(Integer) rs.getObject("display_priority")) : null,
				type == ProfileType.ADMIN ? new ProfileRecord.AdminDetails(
						rs.getString("admin_role"),
						permissions(rs.getArray("permissions"))) : null);
	}

	private static boolean isDriver(ProfileType type) {
		return type == ProfileType.MOTOTAXI || type == ProfileType.TRANSPORTER;
	}

	private static List<String> permissions(Array sqlArray) throws SQLException {
		if (sqlArray == null) {
			return List.of();
		}
		Object value = sqlArray.getArray();
		if (value instanceof String[] strings) {
			return Arrays.asList(strings);
		}
		if (value instanceof Object[] objects) {
			return Arrays.stream(objects).map(String::valueOf).toList();
		}
		return List.of();
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String normalizeAgencyBillingPlan(String billingPlan) {
		if (billingPlan == null || billingPlan.isBlank()) {
			return null;
		}
		String normalized = billingPlan.trim().toUpperCase();
		if ("PREMIUM".equals(normalized) || "STANDARD".equals(normalized)) {
			return normalized;
		}
		return "STANDARD";
	}
}
