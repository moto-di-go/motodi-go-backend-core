package com.motodigo.core.geo.client;

import java.time.Duration;
import java.util.Map;

import com.motodigo.core.geo.config.GeoStackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PhotonControlClient {

	private static final Logger log = LoggerFactory.getLogger(PhotonControlClient.class);

	private final GeoStackProperties properties;
	private final RestClient restClient;

	@Autowired
	public PhotonControlClient(GeoStackProperties properties) {
		this.properties = properties;
		if (!properties.photonControlReady()) {
			this.restClient = null;
			return;
		}
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofMillis(Math.max(200, properties.photonControlTimeoutMs())));
		factory.setReadTimeout(Duration.ofMillis(Math.max(200, properties.photonControlTimeoutMs())));
		this.restClient = RestClient.builder()
				.baseUrl(properties.photonControlUrl())
				.requestFactory(factory)
				.build();
	}

	PhotonControlClient(GeoStackProperties properties, RestClient restClient) {
		this.properties = properties;
		this.restClient = restClient;
	}

	public boolean configured() {
		return this.properties.photonControlReady() && this.restClient != null;
	}

	public ControlStatus status() {
		if (!configured()) {
			return ControlStatus.unavailable("Le controleur Photon n'est pas configure.");
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = this.restClient.get()
					.uri("/status")
					.retrieve()
					.body(Map.class);
			return ControlStatus.from(body);
		}
		catch (RuntimeException ex) {
			log.warn("Photon control status unavailable: {}", ex.getMessage());
			return ControlStatus.unavailable("Le controleur Photon ne repond pas.");
		}
	}

	public ControlStatus startUpdate() {
		if (!configured()) {
			return ControlStatus.unavailable("Le controleur Photon n'est pas configure.");
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = this.restClient.post()
					.uri("/update")
					.retrieve()
					.body(Map.class);
			return ControlStatus.from(body).withHttpStatus(202);
		}
		catch (RestClientResponseException ex) {
			ControlStatus parsed;
			try {
				@SuppressWarnings("unchecked")
				Map<String, Object> body = ex.getResponseBodyAs(Map.class);
				parsed = ControlStatus.from(body).withHttpStatus(ex.getStatusCode().value());
			}
			catch (RuntimeException parseEx) {
				parsed = ControlStatus.unavailable(ex.getStatusText()).withHttpStatus(ex.getStatusCode().value());
			}
			if (parsed.message() == null || parsed.message().isBlank()) {
				return parsed.withMessage(ex.getStatusText());
			}
			return parsed;
		}
		catch (RuntimeException ex) {
			log.warn("Photon control update failed: {}", ex.getMessage());
			return ControlStatus.unavailable("Impossible de lancer la mise a jour Photon.");
		}
	}

	public record ControlStatus(
			boolean ready,
			boolean updating,
			boolean manualUpdateAvailable,
			String message,
			int httpStatus) {

		static ControlStatus unavailable(String message) {
			return new ControlStatus(false, false, false, message, 503);
		}

		static ControlStatus from(Map<String, Object> body) {
			if (body == null) {
				return unavailable("Reponse vide du controleur Photon.");
			}
			return new ControlStatus(
					asBoolean(body.get("ready")),
					asBoolean(body.get("updating")),
					body.get("manualUpdateAvailable") == null || asBoolean(body.get("manualUpdateAvailable")),
					body.get("message") == null ? null : String.valueOf(body.get("message")),
					200);
		}

		ControlStatus withHttpStatus(int status) {
			return new ControlStatus(this.ready, this.updating, this.manualUpdateAvailable, this.message, status);
		}

		ControlStatus withMessage(String message) {
			return new ControlStatus(this.ready, this.updating, this.manualUpdateAvailable, message, this.httpStatus);
		}

		private static boolean asBoolean(Object value) {
			if (value instanceof Boolean bool) {
				return bool;
			}
			return value != null && Boolean.parseBoolean(String.valueOf(value));
		}
	}
}
