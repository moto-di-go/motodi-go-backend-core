package com.motodigo.core.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.geo.entity.BuiltRoute;
import com.motodigo.core.geo.entity.BuiltRouteOption;
import com.motodigo.core.geo.service.RouteBuilder;
import com.motodigo.core.pricing.config.PricingProperties;
import com.motodigo.core.pricing.dto.PricingEstimateRequest;
import com.motodigo.core.pricing.entity.PricingRuleRecord;
import com.motodigo.core.pricing.repository.PricingRuleRepository;
import com.motodigo.core.pricing.weather.OpenMeteoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

	@Mock
	private RouteBuilder routeBuilder;

	@Mock
	private PricingRuleRepository pricingRuleRepository;

	@Mock
	private OpenMeteoClient openMeteoClient;

	private PricingService pricingService;

	@BeforeEach
	void setUp() {
		PricingProperties properties = new PricingProperties(210, LocalTime.of(22, 0), LocalTime.of(5, 0), 300, 0.2);
		this.pricingService = new PricingService(this.routeBuilder, this.pricingRuleRepository, this.openMeteoClient,
				properties, Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void appliesBaseDistanceNightAndWeatherThenRounds() {
		UUID routeId = UUID.randomUUID();
		when(this.routeBuilder.buildRoute(3.86, 11.50, 3.88, 11.54))
				.thenReturn(new BuiltRoute(routeId, 3.86, 11.50, 3.88, 11.54, "LINESTRING(11.50 3.86, 11.54 3.88)",
						10.0, 18, "PROPOSED", List.of(), List.of()));
		when(this.pricingRuleRepository.findActiveForPoint(eq(3.86), eq(11.50), any()))
				.thenReturn(Optional.of(rule()));
		when(this.openMeteoClient.currentPrecipitationMm(anyDouble(), anyDouble(), anyInt())).thenReturn(1.2);
		when(this.pricingRuleRepository.insertFareEstimate(eq(routeId), any(), any(), eq(10.0), eq(18), any(), any()))
				.thenReturn(UUID.randomUUID());

		var estimate = this.pricingService.estimateFare(new PricingEstimateRequest(3.86, 11.50, 3.88, 11.54, "URBAN"));

		assertThat(estimate.amountFcfa()).isEqualByComparingTo("978");
		assertThat(estimate.appliedRulesTrace()).contains("night=1");
		assertThat(estimate.appliedRulesTrace()).contains("weather=1.150");
		assertThat(estimate.expiresAt()).isEqualTo(Instant.parse("2026-08-15T13:30:00Z"));
		assertThat(estimate.breakdown().totalFcfa()).isEqualByComparingTo(estimate.amountFcfa());
		assertThat(estimate.breakdown().baseFcfa()).isEqualByComparingTo("500");
		assertThat(estimate.breakdown().distanceAmountFcfa()).isEqualByComparingTo("350.00");
		assertThat(estimate.breakdown().nightWeatherApplicable()).isTrue();
		assertThat(estimate.breakdown().roundingFcfa()).isEqualByComparingTo("0.50");
	}

	@Test
	void pricesThreeDistinctRouteOptionsAndKeepsBreakdownAlignedWithPrimary() {
		UUID routeId = UUID.randomUUID();
		when(this.routeBuilder.buildRoute(3.86, 11.50, 3.88, 11.54))
				.thenReturn(new BuiltRoute(routeId, 3.86, 11.50, 3.88, 11.54,
						"LINESTRING(11.50 3.86, 11.51 3.87, 11.54 3.88)", 11.4, 45, "PROPOSED", List.of(),
						List.of(
								new BuiltRouteOption(BuiltRouteOption.RECOMMENDED, "Évite routes dégradées", false, 11.4,
										45, "LINESTRING(11.50 3.86, 11.51 3.87, 11.54 3.88)", null),
								new BuiltRouteOption(BuiltRouteOption.FASTEST, "Plus rapide", true, 9.2, 38,
										"LINESTRING(11.50 3.86, 11.54 3.88)", "Route dégradée"),
								new BuiltRouteOption(BuiltRouteOption.CHEAPEST, "Moins cher", false, 10.1, 50,
										"LINESTRING(11.50 3.86, 11.52 3.86, 11.54 3.88)", null))));
		when(this.pricingRuleRepository.findActiveForPoint(eq(3.86), eq(11.50), any()))
				.thenReturn(Optional.of(rule()));
		when(this.openMeteoClient.currentPrecipitationMm(anyDouble(), anyDouble(), anyInt())).thenReturn(0.0);
		when(this.pricingRuleRepository.insertFareEstimate(eq(routeId), any(), any(), eq(11.4), eq(45), any(), any()))
				.thenReturn(UUID.randomUUID());

		var estimate = this.pricingService.estimateFare(new PricingEstimateRequest(3.86, 11.50, 3.88, 11.54, "URBAN"));

		assertThat(estimate.options()).hasSize(3);
		assertThat(estimate.options()).extracting(option -> option.kind())
				.containsExactly("RECOMMENDED", "FASTEST", "CHEAPEST");
		assertThat(estimate.options()).extracting(option -> option.polyline()).doesNotHaveDuplicates();
		assertThat(estimate.options().get(1).degraded()).isTrue();
		assertThat(estimate.polyline()).isEqualTo(estimate.options().getFirst().polyline());
		assertThat(estimate.amountFcfa()).isEqualByComparingTo(estimate.options().getFirst().amountFcfa());
		assertThat(estimate.breakdown().totalFcfa()).isEqualByComparingTo(estimate.amountFcfa());
		assertThat(estimate.options().get(2).amountFcfa())
				.isLessThan(estimate.options().getFirst().amountFcfa());
	}

	private static PricingRuleRecord rule() {
		return new PricingRuleRecord(UUID.randomUUID(), "CM-CE-YDE", null, LocalTime.MIN, LocalTime.of(23, 59, 59),
				new BigDecimal("500"), new BigDecimal("35"), new BigDecimal("1.250"), new BigDecimal("1.150"), true,
				Instant.parse("2026-01-01T00:00:00Z"), null);
	}
}
