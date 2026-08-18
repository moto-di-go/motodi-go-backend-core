package com.motodigo.core.geo.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

import com.motodigo.core.geo.config.GeoProperties;
import com.motodigo.core.geo.entity.BuiltRoute;
import com.motodigo.core.geo.entity.BuiltRouteAlternative;
import com.motodigo.core.geo.entity.BuiltRouteOption;
import com.motodigo.core.geo.exception.GeoErrorCode;
import com.motodigo.core.geo.exception.GeoException;
import com.motodigo.core.geo.repository.RoadNetworkRepository;
import com.motodigo.core.geo.repository.RoadNetworkRepository.BlockingLayer;
import com.motodigo.core.geo.repository.RoadNetworkRepository.RoadEdge;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class RouteBuilder implements RouteEngine {

	static final double DEGRADED_EDGE_PENALTY = 12d;

	private final RoadNetworkRepository roadNetworkRepository;
	private final GeoProperties properties;

	public RouteBuilder(RoadNetworkRepository roadNetworkRepository, GeoProperties properties) {
		this.roadNetworkRepository = roadNetworkRepository;
		this.properties = properties;
	}

	@Override
	public BuiltRoute buildRoute(double originLat, double originLon, double destLat, double destLon) {
		if (!this.properties.isInsideYaounde(originLat, originLon)
				|| !this.properties.isInsideYaounde(destLat, destLon)) {
			throw new GeoException(GeoErrorCode.OUTSIDE_SERVICE_AREA);
		}
		ensureTopology();
		Long originVertex = this.roadNetworkRepository.nearestVertex(originLat, originLon);
		Long destVertex = this.roadNetworkRepository.nearestVertex(destLat, destLon);
		if (originVertex == null || destVertex == null) {
			throw new GeoException(GeoErrorCode.ROUTE_NOT_FOUND);
		}
		List<RoadEdge> edges = this.roadNetworkRepository.loadEdges();
		Set<Long> degradedEdgeIds = new HashSet<>(this.roadNetworkRepository.findDegradedEdgeIds());
		PathResult fastest = dijkstra(edges, originVertex, destVertex, Weight.DURATION, Set.of(), 1d);
		PathResult cheapest = dijkstra(edges, originVertex, destVertex, Weight.DISTANCE, Set.of(), 1d);
		PathResult recommended = dijkstra(edges, originVertex, destVertex, Weight.DURATION, degradedEdgeIds,
				DEGRADED_EDGE_PENALTY);
		if (fastest == null && cheapest == null && recommended == null) {
			throw new GeoException(GeoErrorCode.ROUTE_NOT_FOUND);
		}
		List<BuiltRouteOption> options = enrichWithLayers(
				distinctOptions(recommended, fastest, cheapest, degradedEdgeIds));
		if (options.isEmpty()) {
			throw new GeoException(GeoErrorCode.ROUTE_NOT_FOUND);
		}
		BuiltRouteOption primary = options.getFirst();
		List<BlockingLayer> primaryBlockers = this.roadNetworkRepository.findBlockingLayers(primary.polylineWkt());
		String status = primaryBlockers.isEmpty() ? "PROPOSED" : "BLOCKED";
		List<BuiltRouteAlternative> alternatives = alternativesFrom(options, primary);
		UUID routeId = this.roadNetworkRepository.insertRoute(originLat, originLon, destLat, destLon,
				primary.polylineWkt(), primary.distanceKm(), primary.durationMinutes(), status);
		for (BuiltRouteAlternative alternative : alternatives) {
			this.roadNetworkRepository.insertAlternative(routeId, alternative.reason(), alternative.polylineWkt(),
					alternative.extraDistanceKm(), alternative.extraDurationMinutes());
		}
		return new BuiltRoute(routeId, originLat, originLon, destLat, destLon, primary.polylineWkt(),
				primary.distanceKm(), primary.durationMinutes(), status, alternatives, options);
	}

	static List<BuiltRouteOption> distinctOptions(PathResult recommended, PathResult fastest, PathResult cheapest,
			Set<Long> degradedEdgeIds) {
		LinkedHashMap<String, BuiltRouteOption> distinct = new LinkedHashMap<>();
		addDistinct(distinct, toOption(BuiltRouteOption.RECOMMENDED, recommended, degradedEdgeIds));
		addDistinct(distinct, toOption(BuiltRouteOption.FASTEST, fastest, degradedEdgeIds));
		addDistinct(distinct, toOption(BuiltRouteOption.CHEAPEST, cheapest, degradedEdgeIds));
		return List.copyOf(distinct.values());
	}

	private List<BuiltRouteOption> enrichWithLayers(List<BuiltRouteOption> options) {
		List<BuiltRouteOption> enriched = new ArrayList<>();
		for (BuiltRouteOption option : options) {
			List<BlockingLayer> blockers = this.roadNetworkRepository.findBlockingLayers(option.polylineWkt());
			if (blockers.isEmpty()) {
				enriched.add(option);
				continue;
			}
			String reason = option.reason() == null ? blockers.getFirst().label() : option.reason();
			enriched.add(new BuiltRouteOption(option.kind(), labelFor(option.kind(), true), true, option.distanceKm(),
					option.durationMinutes(), option.polylineWkt(), reason));
		}
		return List.copyOf(enriched);
	}

	private static BuiltRouteOption toOption(String kind, PathResult path, Set<Long> degradedEdgeIds) {
		if (path == null) {
			return null;
		}
		boolean degraded = path.edgeIds().stream().anyMatch(degradedEdgeIds::contains);
		String reason = degraded ? "Route dégradée" : null;
		return new BuiltRouteOption(kind, labelFor(kind, degraded), degraded, path.distanceKm(), path.durationMinutes(),
				path.wkt(), reason);
	}

	private static void addDistinct(LinkedHashMap<String, BuiltRouteOption> distinct, BuiltRouteOption option) {
		if (option == null || distinct.containsKey(option.polylineWkt())) {
			return;
		}
		distinct.put(option.polylineWkt(), option);
	}

	private static List<BuiltRouteAlternative> alternativesFrom(List<BuiltRouteOption> options,
			BuiltRouteOption primary) {
		List<BuiltRouteAlternative> alternatives = new ArrayList<>();
		for (BuiltRouteOption option : options) {
			if (option.kind().equals(primary.kind())) {
				continue;
			}
			String reason = option.reason() == null ? option.label() : option.reason();
			alternatives.add(new BuiltRouteAlternative(
					reason,
					option.polylineWkt(),
					Math.max(0, option.distanceKm() - primary.distanceKm()),
					Math.max(0, option.durationMinutes() - primary.durationMinutes())));
		}
		return List.copyOf(alternatives);
	}

	private static String labelFor(String kind, boolean degraded) {
		return switch (kind) {
			case BuiltRouteOption.RECOMMENDED -> degraded ? "Itinéraire conseillé" : "Évite routes dégradées";
			case BuiltRouteOption.FASTEST -> "Plus rapide";
			case BuiltRouteOption.CHEAPEST -> "Moins cher";
			default -> kind;
		};
	}

	private void ensureTopology() {
		if (this.roadNetworkRepository.loadEdges().isEmpty()) {
			this.roadNetworkRepository.rebuildTopology();
		}
	}

	static PathResult dijkstra(List<RoadEdge> edges, long source, long target, Weight weight,
			Set<Long> penalizedEdgeIds, double penaltyFactor) {
		Map<Long, List<RoadEdge>> adjacency = new HashMap<>();
		for (RoadEdge edge : edges) {
			adjacency.computeIfAbsent(edge.source(), key -> new ArrayList<>()).add(edge);
			adjacency.computeIfAbsent(edge.target(), key -> new ArrayList<>())
					.add(new RoadEdge(edge.id(), edge.target(), edge.source(), reverseWkt(edge.wkt()), edge.lengthM(),
							edge.maxSpeedKmh()));
		}
		Map<Long, Double> dist = new HashMap<>();
		Map<Long, RoadEdge> prev = new HashMap<>();
		PriorityQueue<long[]> queue = new PriorityQueue<>(Comparator.comparingDouble(a -> Double.longBitsToDouble(a[1])));
		dist.put(source, 0d);
		queue.add(new long[] {source, Double.doubleToLongBits(0d)});
		while (!queue.isEmpty()) {
			long[] current = queue.poll();
			long node = current[0];
			double cost = Double.longBitsToDouble(current[1]);
			if (cost > dist.getOrDefault(node, Double.POSITIVE_INFINITY)) {
				continue;
			}
			if (node == target) {
				break;
			}
			for (RoadEdge edge : adjacency.getOrDefault(node, List.of())) {
				double next = cost + edgeCost(edge, weight, penalizedEdgeIds, penaltyFactor);
				if (next < dist.getOrDefault(edge.target(), Double.POSITIVE_INFINITY)) {
					dist.put(edge.target(), next);
					prev.put(edge.target(), edge);
					queue.add(new long[] {edge.target(), Double.doubleToLongBits(next)});
				}
			}
		}
		if (!dist.containsKey(target)) {
			return null;
		}
		List<RoadEdge> used = new ArrayList<>();
		long cursor = target;
		while (cursor != source) {
			RoadEdge edge = prev.get(cursor);
			if (edge == null) {
				return null;
			}
			used.add(edge);
			cursor = edge.source();
		}
		Collections.reverse(used);
		double meters = used.stream().mapToDouble(RoadEdge::lengthM).sum();
		int minutes = (int) Math.max(1, Math.round(used.stream()
				.mapToDouble(RouteBuilder::durationMinutes)
				.sum()));
		return new PathResult(toLineString(used), meters / 1000d, minutes,
				used.stream().map(RoadEdge::id).toList());
	}

	private static double edgeCost(RoadEdge edge, Weight weight, Set<Long> penalizedEdgeIds, double penaltyFactor) {
		double cost = weight == Weight.DISTANCE ? edge.lengthM() : durationMinutes(edge);
		if (penalizedEdgeIds != null && penalizedEdgeIds.contains(edge.id())) {
			return cost * Math.max(penaltyFactor, 1d);
		}
		return cost;
	}

	private static double durationMinutes(RoadEdge edge) {
		return (edge.lengthM() / 1000d) / Math.max(edge.maxSpeedKmh(), 15) * 60d;
	}

	private static String toLineString(List<RoadEdge> edges) {
		List<String> points = new ArrayList<>();
		for (RoadEdge edge : edges) {
			String body = stripLineString(edge.wkt());
			String[] parts = body.split(",");
			if (points.isEmpty()) {
				points.add(parts[0].trim());
			}
			for (int i = 1; i < parts.length; i++) {
				points.add(parts[i].trim());
			}
		}
		return "LINESTRING(" + String.join(", ", points) + ")";
	}

	private static String reverseWkt(String wkt) {
		String[] parts = stripLineString(wkt).split(",");
		List<String> reversed = new ArrayList<>();
		for (int i = parts.length - 1; i >= 0; i--) {
			reversed.add(parts[i].trim());
		}
		return "LINESTRING(" + String.join(", ", reversed) + ")";
	}

	private static String stripLineString(String wkt) {
		return wkt.replace("LINESTRING", "").replace("(", "").replace(")", "").trim();
	}

	enum Weight {
		DURATION,
		DISTANCE
	}

	record PathResult(String wkt, double distanceKm, int durationMinutes, List<Long> edgeIds) {
	}
}
