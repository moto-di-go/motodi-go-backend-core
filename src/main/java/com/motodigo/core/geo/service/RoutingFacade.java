package com.motodigo.core.geo.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.motodigo.core.geo.client.ValhallaClient;
import com.motodigo.core.geo.client.ValhallaPath;
import com.motodigo.core.geo.config.GeoProperties;
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
	public static final String ENGINE_DIRECT = "DIRECT";

	private final RouteBuilder routeBuilder;
	private final ValhallaClient valhallaClient;
	private final RoadNetworkRepository roadNetworkRepository;
	private final GeoProperties geoProperties;

	public RoutingFacade(RouteBuilder routeBuilder, ValhallaClient valhallaClient,
			RoadNetworkRepository roadNetworkRepository, GeoProperties geoProperties) {
		this.routeBuilder = routeBuilder;
		this.valhallaClient = valhallaClient;
		this.roadNetworkRepository = roadNetworkRepository;
		this.geoProperties = geoProperties;
	}

	@Override
	public BuiltRoute buildRoute(double originLat, double originLon, double destLat, double destLon) {
		return preview(originLat, originLon, destLat, destLon).route();
	}

	public RoutedResult preview(double originLat, double originLon, double destLat, double destLon) {
		return preview(originLat, originLon, destLat, destLon, null);
	}

	public RoutedResult preview(double originLat, double originLon, double destLat, double destLon, String costing) {
		List<ValhallaPath> paths = this.valhallaClient.route(originLat, originLon, destLat, destLon, costing);
		if (paths.isEmpty()) {
			return fallback(originLat, originLon, destLat, destLon);
		}
		List<BuiltRouteOption> options = enrich(toOptions(paths));
		if (options.isEmpty()) {
			return fallback(originLat, originLon, destLat, destLon);
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

	private RoutedResult fallback(double originLat, double originLon, double destLat, double destLon) {
		boolean bothInYaounde = this.geoProperties.isInsideYaounde(originLat, originLon)
				&& this.geoProperties.isInsideYaounde(destLat, destLon);
		if (bothInYaounde) {
			return new RoutedResult(this.routeBuilder.buildRoute(originLat, originLon, destLat, destLon), ENGINE_LOCAL);
		}
		return new RoutedResult(directRoute(originLat, originLon, destLat, destLon), ENGINE_DIRECT);
	}

	static BuiltRoute directRoute(double originLat, double originLon, double destLat, double destLon) {
		String wkt = String.format(Locale.US, "LINESTRING(%f %f, %f %f)", originLon, originLat, destLon, destLat);
		double km = Math.round(haversineKm(originLat, originLon, destLat, destLon) * 1000d) / 1000d;
		int minutes = Math.max(1, (int) Math.round(km));
		BuiltRouteOption option = new BuiltRouteOption(BuiltRouteOption.RECOMMENDED, "Itinéraire conseillé", true, km,
				minutes, wkt, "DIRECT");
		return new BuiltRoute(UUID.randomUUID(), originLat, originLon, destLat, destLon, wkt, km, minutes, "PROPOSED",
				List.of(), List.of(option));
	}

	private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
		double earth = 6371;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return earth * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
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
