package com.motodigo.core.urban.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.geo.client.PhotonClient;
import com.motodigo.core.urban.dto.UrbanPlaceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceSearchFacadeTest {

	@Mock
	private UrbanPlaceService urbanPlaceService;

	@Mock
	private PhotonClient photonClient;

	private PlaceSearchFacade facade;

	@BeforeEach
	void setUp() {
		this.facade = new PlaceSearchFacade(this.urbanPlaceService, this.photonClient);
	}

	@Test
	void ranksLocalPlacesAheadOfPhotonAndDropsNearDuplicates() {
		UrbanPlaceResponse local = UrbanPlaceResponse.local(UUID.randomUUID(), "Playce Yaoundé", 3.868, 11.516, "POI");
		UrbanPlaceResponse photonDup = new UrbanPlaceResponse(UUID.randomUUID(), "Playce Yaounde", 3.8681, 11.5161,
				"POI", "Shopping mall", "PHOTON", null);
		UrbanPlaceResponse photonExtra = new UrbanPlaceResponse(UUID.randomUUID(), "Carrefour Playce", 3.87, 11.52,
				"POI", "Supermarket · Ekoudou", "PHOTON", null);
		when(this.urbanPlaceService.geocode(eq("playce"), isNull(), eq(3.868), eq(11.516))).thenReturn(List.of(local));
		when(this.photonClient.search(eq("playce"), eq(3.868), eq(11.516), anyInt(), isNull()))
				.thenReturn(List.of(photonDup, photonExtra));

		List<UrbanPlaceResponse> results = this.facade.suggest("playce", 3.868, 11.516);

		assertThat(results).hasSize(2);
		assertThat(results.getFirst().source()).isEqualTo("LOCAL");
		assertThat(results.getFirst().label()).isEqualTo("Playce Yaoundé");
		assertThat(results.get(1).label()).isEqualTo("Carrefour Playce");
		assertThat(results.get(1).source()).isEqualTo("PHOTON");
		assertThat(results.getFirst().distanceKm()).isNotNull();
	}

	@Test
	void citySuggestUsesKindAndDoesNotBiasToYaounde() {
		UrbanPlaceResponse douala = UrbanPlaceResponse.local(UUID.randomUUID(), "Douala", 4.05, 9.76, "CITY");
		when(this.urbanPlaceService.geocode(eq("dou"), eq("CITY"), isNull(), isNull())).thenReturn(List.of(douala));
		when(this.photonClient.search(eq("dou"), isNull(), isNull(), anyInt(), eq("CITY")))
				.thenReturn(List.of());

		List<UrbanPlaceResponse> results = this.facade.suggest("dou", 3.868, 11.516, "CITY");

		assertThat(results).extracting(UrbanPlaceResponse::label).containsExactly("Douala");
	}
}
