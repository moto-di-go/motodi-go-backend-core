package com.motodigo.core.geo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.motodigo.core.geo.client.ValhallaPath;
import com.motodigo.core.geo.entity.BuiltRouteOption;
import org.junit.jupiter.api.Test;

class RoutingFacadeTest {

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
}
