package com.motodigo.core.pricing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.geo.entity.BuiltRoute;
import com.motodigo.core.geo.entity.BuiltRouteOption;
import com.motodigo.core.geo.service.RouteBuilder;
import com.motodigo.core.pricing.config.PricingProperties;
import com.motodigo.core.pricing.dto.PricingBreakdown;
import com.motodigo.core.pricing.dto.PricingEstimateRequest;
import com.motodigo.core.pricing.dto.PricingEstimateResponse;
import com.motodigo.core.pricing.dto.PricingRouteOption;
import com.motodigo.core.pricing.entity.PricingRuleRecord;
import com.motodigo.core.pricing.exception.PricingErrorCode;
import com.motodigo.core.pricing.exception.PricingException;
import com.motodigo.core.pricing.repository.PricingRuleRepository;
import com.motodigo.core.pricing.weather.OpenMeteoClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PricingService {

	public static final ZoneId APP_ZONE = ZoneId.of("Africa/Douala");

	private final RouteBuilder routeBuilder;
	private final PricingRuleRepository pricingRuleRepository;
	private final OpenMeteoClient openMeteoClient;
	private final PricingProperties properties;
	private final Clock clock;

	public PricingService(RouteBuilder routeBuilder, PricingRuleRepository pricingRuleRepository,
			OpenMeteoClient openMeteoClient, PricingProperties properties) {
		this(routeBuilder, pricingRuleRepository, openMeteoClient, properties, Clock.systemUTC());
	}

	PricingService(RouteBuilder routeBuilder, PricingRuleRepository pricingRuleRepository,
			OpenMeteoClient openMeteoClient, PricingProperties properties, Clock clock) {
		this.routeBuilder = routeBuilder;
		this.pricingRuleRepository = pricingRuleRepository;
		this.openMeteoClient = openMeteoClient;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public PricingEstimateResponse estimateFare(PricingEstimateRequest request) {
		if (request.originLat() == null || request.originLon() == null || request.destinationLat() == null
				|| request.destinationLon() == null) {
			throw new PricingException(PricingErrorCode.ESTIMATE_INVALID);
		}
		BuiltRoute route = this.routeBuilder.buildRoute(request.originLat(), request.originLon(),
				request.destinationLat(), request.destinationLon());
		Instant now = Instant.now(this.clock);
		PricingRuleRecord rule = this.pricingRuleRepository.findActiveForPoint(request.originLat(), request.originLon(), now)
				.filter(candidate -> matchesWindow(candidate, ZonedDateTime.ofInstant(now, APP_ZONE).toLocalTime()))
				.orElseThrow(() -> new PricingException(PricingErrorCode.NO_PRICING_RULE));
		boolean night = this.properties.isNight(ZonedDateTime.ofInstant(now, APP_ZONE).toLocalTime());
		double precipitation = this.openMeteoClient.currentPrecipitationMm(request.originLat(), request.originLon(),
				this.properties.weatherCacheSeconds());
		boolean rainy = precipitation >= this.properties.rainThresholdMm();
		FareQuote primary = quote(rule, route.distanceKm(), night, rainy, precipitation);
		List<PricingRouteOption> options = priceOptions(route, rule, night, rainy, precipitation);
		Instant expiresAt = now.plus(Duration.ofMinutes(this.properties.fareTtlMinutes()));
		UUID estimateId = this.pricingRuleRepository.insertFareEstimate(route.id(), rule.id(), primary.amount(),
				route.distanceKm(), route.durationMinutes(), primary.trace(), expiresAt);
		return new PricingEstimateResponse(estimateId, route.id(), primary.amount(), route.distanceKm(),
				route.durationMinutes(), primary.trace(), route.polylineWkt(), route.status(), route.alternatives(), now,
				expiresAt, primary.breakdown(), options);
	}

	private List<PricingRouteOption> priceOptions(BuiltRoute route, PricingRuleRecord rule, boolean night,
			boolean rainy, double precipitation) {
		List<BuiltRouteOption> built = route.options();
		if (built.isEmpty()) {
			FareQuote quote = quote(rule, route.distanceKm(), night, rainy, precipitation);
			return List.of(new PricingRouteOption(BuiltRouteOption.RECOMMENDED, "Évite routes dégradées", false,
					route.distanceKm(), route.durationMinutes(), quote.amount(), route.polylineWkt(), null));
		}
		List<PricingRouteOption> priced = new ArrayList<>();
		for (BuiltRouteOption option : built) {
			FareQuote quote = quote(rule, option.distanceKm(), night, rainy, precipitation);
			priced.add(new PricingRouteOption(option.kind(), option.label(), option.degraded(), option.distanceKm(),
					option.durationMinutes(), quote.amount(), option.polylineWkt(), option.reason()));
		}
		return List.copyOf(priced);
	}

	static FareQuote quote(PricingRuleRecord rule, double distanceKm, boolean night, boolean rainy,
			double precipitation) {
		BigDecimal nightCoeff = night ? rule.nightCoefficient() : BigDecimal.ONE;
		BigDecimal weatherCoeff = rainy ? rule.weatherCoefficient() : BigDecimal.ONE;
		BigDecimal distance = BigDecimal.valueOf(distanceKm);
		BigDecimal distanceAmount = distance.multiply(rule.pricePerKm());
		BigDecimal raw = rule.baseFare().add(distanceAmount);
		BigDecimal rounded = raw.setScale(0, RoundingMode.HALF_UP);
		BigDecimal amount = rounded.multiply(nightCoeff).multiply(weatherCoeff).setScale(0, RoundingMode.HALF_UP);
		BigDecimal unroundedTotal = raw.multiply(nightCoeff).multiply(weatherCoeff);
		BigDecimal rounding = amount.subtract(unroundedTotal).setScale(2, RoundingMode.HALF_UP);
		String trace = "base %s + %skm*%s, arrondi, night=%s, weather=%s (precip=%smm)"
				.formatted(rule.baseFare().toPlainString(), distance.setScale(3, RoundingMode.HALF_UP),
						rule.pricePerKm().toPlainString(), nightCoeff, weatherCoeff, precipitation);
		PricingBreakdown breakdown = new PricingBreakdown(
				rule.baseFare(),
				distanceKm,
				rule.pricePerKm(),
				distanceAmount.setScale(2, RoundingMode.HALF_UP),
				nightCoeff,
				weatherCoeff,
				night || rainy,
				rounding,
				amount);
		return new FareQuote(amount, trace, breakdown);
	}

	static boolean matchesWindow(PricingRuleRecord rule, LocalTime time) {
		if (!rule.timeStart().isAfter(rule.timeEnd())) {
			return !time.isBefore(rule.timeStart()) && !time.isAfter(rule.timeEnd());
		}
		return !time.isBefore(rule.timeStart()) || !time.isAfter(rule.timeEnd());
	}

	record FareQuote(BigDecimal amount, String trace, PricingBreakdown breakdown) {
	}
}
