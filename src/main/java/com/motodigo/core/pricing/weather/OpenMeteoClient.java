package com.motodigo.core.pricing.weather;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenMeteoClient {

	private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

	private final RestClient restClient;
	private final Map<String, CachedPrecipitation> cache = new ConcurrentHashMap<>();

	@Autowired
	public OpenMeteoClient() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofMillis(1500));
		factory.setReadTimeout(Duration.ofMillis(2000));
		this.restClient = RestClient.builder()
				.baseUrl("https://api.open-meteo.com")
				.requestFactory(factory)
				.build();
	}

	OpenMeteoClient(RestClient restClient) {
		this.restClient = restClient;
	}

	@SuppressWarnings("unchecked")
	public double currentPrecipitationMm(double lat, double lon, int cacheSeconds) {
		String key = Math.round(lat * 100) + ":" + Math.round(lon * 100);
		CachedPrecipitation cached = this.cache.get(key);
		if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
			return cached.mm();
		}
		try {
			Map<String, Object> body = this.restClient.get()
					.uri(uri -> uri.path("/v1/forecast")
							.queryParam("latitude", lat)
							.queryParam("longitude", lon)
							.queryParam("current", "precipitation")
							.build())
					.retrieve()
					.body(Map.class);
			double mm = 0;
			if (body != null && body.get("current") instanceof Map<?, ?> current
					&& current.get("precipitation") instanceof Number number) {
				mm = number.doubleValue();
			}
			this.cache.put(key, new CachedPrecipitation(mm, Instant.now().plusSeconds(cacheSeconds)));
			return mm;
		}
		catch (RuntimeException ex) {
			log.warn("Open-Meteo unavailable, defaulting precipitation to 0: {}", ex.getMessage());
			return cached == null ? 0 : cached.mm();
		}
	}

	private record CachedPrecipitation(double mm, Instant expiresAt) {
	}
}
