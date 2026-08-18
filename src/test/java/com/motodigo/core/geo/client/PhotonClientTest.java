package com.motodigo.core.geo.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PhotonClientTest {

	@Test
	void parsesPhotonGeoJsonFeatures() {
		Map<String, Object> body = Map.of("features", List.of(Map.of(
				"geometry", Map.of("coordinates", List.of(11.516, 3.868)),
				"properties", Map.of(
						"osm_id", 42,
						"name", "Carrefour Playce",
						"osm_key", "shop",
						"osm_value", "mall",
						"city", "Yaoundé"))));

		List<PhotonPlace> places = PhotonClient.parseFeatures(body);

		assertThat(places).hasSize(1);
		assertThat(places.getFirst().name()).isEqualTo("Carrefour Playce");
		assertThat(places.getFirst().kind()).isEqualTo("POI");
		assertThat(places.getFirst().lat()).isEqualTo(3.868);
		assertThat(PhotonClient.toResponses(places).getFirst().source()).isEqualTo("PHOTON");
	}

	@Test
	void mapsCityOsmValueToCityKind() {
		Map<String, Object> body = Map.of("features", List.of(Map.of(
				"geometry", Map.of("coordinates", List.of(9.7679, 4.0511)),
				"properties", Map.of(
						"osm_id", 7,
						"name", "Douala",
						"osm_key", "place",
						"osm_value", "city",
						"type", "city"))));

		assertThat(PhotonClient.parseFeatures(body).getFirst().kind()).isEqualTo("CITY");
	}
}
