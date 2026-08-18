package com.motodigo.core.geo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.motodigo.core.geo.client.ValhallaClient;
import com.motodigo.core.geo.client.ValhallaPath;
import com.motodigo.core.geo.config.GeoProperties;
import com.motodigo.core.geo.entity.BuiltRoute;
import com.motodigo.core.geo.entity.BuiltRouteOption;
import com.motodigo.core.geo.repository.RoadNetworkRepository;
import com.motodigo.core.geo.service.RoutingFacade.RoutedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutingFacadeTest {

	@Mock
	private RouteBuilder routeBuilder;

	@Mock
	private ValhallaClient valhallaClient;

	@Mock
	private RoadNetworkRepository roadNetworkRepository;

	private RoutingFacade facade;

	@BeforeEach
	void setUp() {
		GeoProperties properties = new GeoProperties(3, 5, 60, 24, 2, 10, 3.72, 4.05, 11.38, 11.64,
				"./data/gis/routes_yaounde.gpkg");
		this.facade = new RoutingFacade(this.routeBuilder, this.valhallaClient, this.roadNetworkRepository, properties);
	}

	@Test
	void mapsValhallaPathsToRecommendedFastestCheapestWithoutDuplicates() {
		List<BuiltRouteOption> options = RoutingFacade.toOptions(List.of(
				new ValhallaPath("LINESTRING(11.50 3.86, 11.54 3.88)", 4.2, 9),
				new ValhallaPath("LINESTRING(11.50 3.86, 11.52 3.87, 11.54 3.88)", 4.8, 11),
				new ValhallaPath("LINESTRING(11.50 3.86, 11.51 3.86, 11.54 3.88)", 3.9, 12),
				new ValhallaPath("LINESTRING(11.50 3.86, 11.54 3.88)", 4.2, 9)));

		assertThat(options).hasSize(3);
		assertThat(options).extracting(BuiltRouteOption::kind)
				.containsExactly("RECOMMENDED", "FASTEST", "CHEAPEST");
		assertThat(options).extracting(BuiltRouteOption::polylineWkt).doesNotHaveDuplicates();
	}

	@Test
	void fallsBackToDirectLineOutsideYaoundeWhenValhallaIsEmpty() {
		when(this.valhallaClient.route(4.0511, 9.7679, 3.8667, 11.5167, null)).thenReturn(List.of());

		RoutedResult result = this.facade.preview(4.0511, 9.7679, 3.8667, 11.5167);

		assertThat(result.engine()).isEqualTo(RoutingFacade.ENGINE_DIRECT);
		assertThat(result.route().polylineWkt()).contains("LINESTRING");
		assertThat(result.route().distanceKm()).isGreaterThan(100);
		verify(this.routeBuilder, never()).buildRoute(4.0511, 9.7679, 3.8667, 11.5167);
	}

	@Test
	void buildsDirectLineStringBetweenCities() {
		BuiltRoute route = RoutingFacade.directRoute(4.0511, 9.7679, 3.8667, 11.5167);

		assertThat(route.polylineWkt()).isEqualTo("LINESTRING(9.767900 4.051100, 11.516700 3.866700)");
		assertThat(route.options()).hasSize(1);
		assertThat(route.options().getFirst().degraded()).isTrue();
	}
}
