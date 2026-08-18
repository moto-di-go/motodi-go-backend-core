package com.motodigo.core.geo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.motodigo.core.geo.config.GeoProperties;
import com.motodigo.core.geo.entity.BuiltRouteOption;
import com.motodigo.core.geo.repository.RoadNetworkRepository;
import com.motodigo.core.geo.repository.RoadNetworkRepository.RoadEdge;
import com.motodigo.core.geo.service.RouteBuilder.PathResult;
import com.motodigo.core.geo.service.RouteBuilder.Weight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RouteBuilderTest {

	@Mock
	private RoadNetworkRepository roadNetworkRepository;

	private RouteBuilder routeBuilder;

	@BeforeEach
	void setUp() {
		GeoProperties properties = new GeoProperties(3, 5, 60, 24, 2, 10, 3.72, 4.05, 11.38, 11.64,
				"./data/gis/routes_yaounde.gpkg");
		this.routeBuilder = new RouteBuilder(this.roadNetworkRepository, properties);
	}

	@Test
	void computesThreeDistinctOptionsWhenGraphHasFastCheapAndAvoidingPaths() {
		List<RoadEdge> edges = divergingGraph();
		when(this.roadNetworkRepository.loadEdges()).thenReturn(edges);
		when(this.roadNetworkRepository.nearestVertex(anyDouble(), anyDouble())).thenReturn(1L, 3L);
		when(this.roadNetworkRepository.findDegradedEdgeIds()).thenReturn(List.of(10L));
		when(this.roadNetworkRepository.findBlockingLayers(anyString())).thenReturn(List.of());
		when(this.roadNetworkRepository.insertRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString(),
				anyDouble(), anyInt(), anyString())).thenReturn(UUID.randomUUID());

		var route = this.routeBuilder.buildRoute(3.86, 11.50, 3.88, 11.54);

		assertThat(route.options()).hasSize(3);
		assertThat(route.options()).extracting(BuiltRouteOption::kind)
				.containsExactly("RECOMMENDED", "FASTEST", "CHEAPEST");
		assertThat(route.options()).extracting(BuiltRouteOption::polylineWkt).doesNotHaveDuplicates();
		assertThat(route.options().getFirst().degraded()).isFalse();
		assertThat(route.options().stream().filter(option -> "FASTEST".equals(option.kind())).findFirst())
				.get()
				.extracting(BuiltRouteOption::degraded)
				.isEqualTo(true);
		assertThat(route.polylineWkt()).isEqualTo(route.options().getFirst().polylineWkt());
	}

	@Test
	void returnsOnlyDistinctGeometriesWhenCriteriaCollapse() {
		PathResult only = new PathResult("LINESTRING(11.50 3.86, 11.54 3.88)", 4.4, 7, List.of(1L));

		List<BuiltRouteOption> options = RouteBuilder.distinctOptions(only, only, only, Set.of());

		assertThat(options).hasSize(1);
		assertThat(options.getFirst().kind()).isEqualTo("RECOMMENDED");
	}

	@Test
	void fastestUsesDurationAndCheapestUsesDistance() {
		List<RoadEdge> edges = divergingGraph();

		PathResult fastest = RouteBuilder.dijkstra(edges, 1L, 3L, Weight.DURATION, Set.of(), 1d);
		PathResult cheapest = RouteBuilder.dijkstra(edges, 1L, 3L, Weight.DISTANCE, Set.of(), 1d);
		PathResult recommended = RouteBuilder.dijkstra(edges, 1L, 3L, Weight.DURATION, Set.of(10L),
				RouteBuilder.DEGRADED_EDGE_PENALTY);

		assertThat(fastest.wkt()).isNotEqualTo(cheapest.wkt());
		assertThat(recommended.wkt()).isNotEqualTo(fastest.wkt());
		assertThat(recommended.wkt()).isNotEqualTo(cheapest.wkt());
		assertThat(cheapest.distanceKm()).isLessThan(fastest.distanceKm());
		assertThat(fastest.durationMinutes()).isLessThan(cheapest.durationMinutes());
	}

	/**
	 * Graphe synthétique où les 3 critères divergent :
	 * FASTEST = arête 10 (rapide, longue, dégradée),
	 * CHEAPEST = 20+21 (courte, lente),
	 * RECOMMENDED = 30+31 (évite l'arête dégradée).
	 */
	private static List<RoadEdge> divergingGraph() {
		return List.of(
				new RoadEdge(10L, 1L, 3L, "LINESTRING(11.50 3.86, 11.54 3.88)", 10000, 100),
				new RoadEdge(20L, 1L, 2L, "LINESTRING(11.50 3.86, 11.52 3.86)", 3000, 20),
				new RoadEdge(21L, 2L, 3L, "LINESTRING(11.52 3.86, 11.54 3.88)", 3000, 20),
				new RoadEdge(30L, 1L, 4L, "LINESTRING(11.50 3.86, 11.51 3.87)", 4000, 50),
				new RoadEdge(31L, 4L, 3L, "LINESTRING(11.51 3.87, 11.54 3.88)", 4000, 50));
	}
}
