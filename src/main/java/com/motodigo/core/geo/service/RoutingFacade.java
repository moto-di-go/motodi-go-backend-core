package com.motodigo.core.geo.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import com.motodigo.core.geo.client.ValhallaClient;
import com.motodigo.core.geo.client.ValhallaPath;
import com.motodigo.core.geo.entity.BuiltRoute;
import com.motodigo.core.geo.entity.BuiltRouteAlternative;
import com.motodigo.core.geo.entity.BuiltRouteOption;
import com.motodigo.core.geo.repository.RoadNetworkRepository;
import com.motodigo.core.geo.repository.RoadNetworkRepository.BlockingLayer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class RoutingFacade implements RouteEngine {

	public static final String ENGINE_VALHALLA = "VALHALLA";
	public static final String ENGINE_LOCAL = "LOCAL";

	private final RouteBuilder routeBuilder;
	private final ValhallaClient valhallaClient;
	private final RoadNetworkRepository roadNetworkRepository;

	public RoutingFacade(RouteBuilder routeBuilder, ValhallaClient valhallaClient,
			RoadNetworkRepository roadNetworkRepository) {
		this.routeBuilder = routeBuilder;
		this.valhallaClient = valhallaClient;
		this.roadNetworkRepository = roadNetworkRepository;
	}

	@Override
	public BuiltRoute buildRoute(double originLat, double originLon, double destLat, double destLon) {
		return preview(originLat, originLon, destLat, destLon).route();
	}

	public RoutedResult preview(double originLat, double originLon, double destLat, double destLon) {
		List<ValhallaPath> paths = this.valhallaClient.route(originLat, originLon, destLat, destLon);
		if (paths.isEmpty()) {
			return new RoutedResult(this.routeBuilder.buildRoute(originLat, originLon, destLat, destLon), ENGINE_LOCAL);
		}
		List<BuiltRouteOption> options = enrich(toOptions(paths));
		if (options.isEmpty()) {
			return new RoutedResult(this.routeBuilder.buildRoute(originLat, originLon, destLat, destLon), ENGINE_LOCAL);
		}
		BuiltRouteOption primary = options.getFirst();
		List<BlockingLayer> blockers = this.roadNetworkRepository.findBlockingLayers(primary.polylineWkt());
		String status = blockers.isEmpty() ? "PROPOSED" : "BLOCKED";
		List<BuiltRouteAlternative> alternatives = alternativesFrom(options, primary);
		UUID routeId = this.roadNetworkRepository.insertRoute(originLat, originLon, destLat, destLon,
				primary.polylineWkt(), primary.distanceKm(), primary.durationMinutes(), status);
		for (BuiltRouteAlternative alternative : alternatives) {
			this.roadNetworkRepository.insertAlternative(routeId, alternative.reason(), alternative.polylineWkt(),
					alternative.extraDistanceKm(), alternative.extraDurationMinutes());
		}
		return new RoutedResult(new BuiltRoute(routeId, originLat, originLon, destLat, destLon, primary.polylineWkt(),
				primary.distanceKm(), primary.durationMinutes(), status, alternatives, options), ENGINE_VALHALLA);
	}

	public record RoutedResult(BuiltRoute route, String engine) {
	}

	public ValhallaPath match(List<double[]> latLonPoints) {
		return this.valhallaClient.match(latLonPoints);
	}

	static List<BuiltRouteOption> toOptions(List<ValhallaPath> paths) {
		LinkedHashMap<String, BuiltRouteOption> distinct = new LinkedHashMap<>();
		int index = 0;
		for (ValhallaPath path : paths) {
			if (path == null || distinct.containsKey(path.polylineWkt())) {
				continue;
			}
			String kind = switch (index) {
				case 0 -> BuiltRouteOption.RECOMMENDED;
				case 1 -> BuiltRouteOption.FASTEST;
				default -> BuiltRouteOption.CHEAPEST;
			};
			String label = switch (kind) {
				case BuiltRouteOption.FASTEST -> "Plus rapide";
				case BuiltRouteOption.CHEAPEST -> "Moins cher";
				default -> "Itinéraire conseillé";
			};
			distinct.put(path.polylineWkt(), new BuiltRouteOption(kind, label, false, path.distanceKm(),
					path.durationMinutes(), path.polylineWkt(), null));
			index++;
			if (distinct.size() == 3) {
				break;
			}
		}
		return List.copyOf(distinct.values());
	}

	private List<BuiltRouteOption> enrich(List<BuiltRouteOption> options) {
		List<BuiltRouteOption> enriched = new ArrayList<>();
		for (BuiltRouteOption option : options) {
			List<BlockingLayer> blockers = this.roadNetworkRepository.findBlockingLayers(option.polylineWkt());
			if (blockers.isEmpty()) {
				enriched.add(option);
				continue;
			}
			String reason = option.reason() == null ? blockers.getFirst().label() : option.reason();
			enriched.add(new BuiltRouteOption(option.kind(), option.label(), true, option.distanceKm(),
					option.durationMinutes(), option.polylineWkt(), reason));
		}
		return List.copyOf(enriched);
	}

	private static List<BuiltRouteAlternative> alternativesFrom(List<BuiltRouteOption> options,
			BuiltRouteOption primary) {
		List<BuiltRouteAlternative> alternatives = new ArrayList<>();
		for (BuiltRouteOption option : options) {
			if (option.kind().equals(primary.kind())) {
				continue;
			}
			String reason = option.reason() == null ? option.label() : option.reason();
			alternatives.add(new BuiltRouteAlternative(reason, option.polylineWkt(),
					Math.max(0, option.distanceKm() - primary.distanceKm()),
					Math.max(0, option.durationMinutes() - primary.durationMinutes())));
		}
		return List.copyOf(alternatives);
	}
}
