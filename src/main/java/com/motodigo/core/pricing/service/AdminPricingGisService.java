package com.motodigo.core.pricing.service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.motodigo.core.pricing.dto.PricingRuleRequest;
import com.motodigo.core.pricing.dto.TrafficLayerRequest;
import com.motodigo.core.pricing.dto.TrafficLayerResponse;
import com.motodigo.core.pricing.entity.PricingRuleRecord;
import com.motodigo.core.pricing.exception.PricingErrorCode;
import com.motodigo.core.pricing.exception.PricingException;
import com.motodigo.core.pricing.repository.PricingRuleRepository;
import com.motodigo.core.pricing.repository.TrafficLayerRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AdminPricingGisService {

	private static final Set<String> LAYER_TYPES = Set.of("CONGESTION", "ROADWORK", "DAMAGED_ROAD", "ALTERNATIVE_ROUTE");
	private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

	private final PricingRuleRepository pricingRuleRepository;
	private final TrafficLayerRepository trafficLayerRepository;
	private final JdbcTemplate jdbcTemplate;

	public AdminPricingGisService(PricingRuleRepository pricingRuleRepository,
			TrafficLayerRepository trafficLayerRepository, JdbcTemplate jdbcTemplate) {
		this.pricingRuleRepository = pricingRuleRepository;
		this.trafficLayerRepository = trafficLayerRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public List<PricingRuleRecord> listRules() {
		return this.pricingRuleRepository.findAll();
	}

	@Transactional
	public PricingRuleRecord createRule(PricingRuleRequest request, UUID adminProfileId) {
		requirePermissionContext(adminProfileId);
		validateWkt(request.zoneWkt(), true);
		return this.pricingRuleRepository.insert(
				request.zoneCode(),
				request.zoneWkt(),
				request.timeStart() == null ? LocalTime.MIN : request.timeStart(),
				request.timeEnd() == null ? LocalTime.of(23, 59, 59) : request.timeEnd(),
				request.baseFare(),
				request.pricePerKm(),
				defaultCoeff(request.nightCoefficient()),
				defaultCoeff(request.weatherCoefficient()),
				request.active() == null || request.active(),
				adminProfileId);
	}

	@Transactional
	public PricingRuleRecord updateRule(UUID id, PricingRuleRequest request, UUID adminProfileId) {
		requirePermissionContext(adminProfileId);
		this.pricingRuleRepository.findById(id)
				.orElseThrow(() -> new PricingException(PricingErrorCode.RULE_NOT_FOUND));
		validateWkt(request.zoneWkt(), true);
		return this.pricingRuleRepository.update(id, request.zoneCode(), request.zoneWkt(), request.timeStart(),
				request.timeEnd(), request.baseFare(), request.pricePerKm(), request.nightCoefficient(),
				request.weatherCoefficient(), request.active());
	}

	@Transactional(readOnly = true)
	public List<TrafficLayerResponse> listLayers(boolean activeOnly) {
		return activeOnly ? this.trafficLayerRepository.findActive() : this.trafficLayerRepository.findAll();
	}

	@Transactional
	public TrafficLayerResponse createLayer(TrafficLayerRequest request, UUID adminProfileId) {
		requirePermissionContext(adminProfileId);
		validateLayer(request);
		return this.trafficLayerRepository.insert(
				request.type(),
				request.label(),
				request.geometryWkt(),
				request.severity() == null ? "LOW" : request.severity(),
				request.validFrom(),
				request.validTo(),
				request.active() == null || request.active(),
				request.source(),
				adminProfileId);
	}

	@Transactional
	public TrafficLayerResponse updateLayer(UUID id, TrafficLayerRequest request, UUID adminProfileId) {
		requirePermissionContext(adminProfileId);
		this.trafficLayerRepository.findById(id)
				.orElseThrow(() -> new PricingException(PricingErrorCode.LAYER_NOT_FOUND));
		if (request.geometryWkt() != null) {
			validateWkt(request.geometryWkt(), false);
		}
		if (request.type() != null && !LAYER_TYPES.contains(request.type())) {
			throw new PricingException(PricingErrorCode.LAYER_INVALID);
		}
		return this.trafficLayerRepository.update(id, request.type(), request.label(), request.geometryWkt(),
				request.severity(), request.validFrom(), request.validTo(), request.active(), request.source());
	}

	private void validateLayer(TrafficLayerRequest request) {
		if (!LAYER_TYPES.contains(request.type())) {
			throw new PricingException(PricingErrorCode.LAYER_INVALID, "Type de couche inconnu.");
		}
		if (request.severity() != null && !SEVERITIES.contains(request.severity())) {
			throw new PricingException(PricingErrorCode.LAYER_INVALID, "Severite invalide.");
		}
		validateWkt(request.geometryWkt(), false);
	}

	private void validateWkt(String wkt, boolean polygonPreferred) {
		if (wkt == null || wkt.isBlank()) {
			if (polygonPreferred) {
				return;
			}
			throw new PricingException(PricingErrorCode.LAYER_INVALID, "Geometrie WKT requise.");
		}
		Boolean valid = this.jdbcTemplate.queryForObject("SELECT ST_IsValid(ST_GeomFromText(?, 4326))", Boolean.class, wkt);
		if (!Boolean.TRUE.equals(valid)) {
			throw new PricingException(polygonPreferred ? PricingErrorCode.RULE_INVALID : PricingErrorCode.LAYER_INVALID,
					"Geometrie invalide.");
		}
	}

	private static void requirePermissionContext(UUID adminProfileId) {
		if (adminProfileId == null) {
			throw new PricingException(PricingErrorCode.ADMIN_REQUIRED);
		}
	}

	private static BigDecimal defaultCoeff(BigDecimal value) {
		return value == null ? BigDecimal.ONE : value;
	}
}
