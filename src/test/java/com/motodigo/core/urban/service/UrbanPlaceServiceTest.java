package com.motodigo.core.urban.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.geo.config.GeoProperties;
import com.motodigo.core.urban.entity.UrbanPlaceRecord;
import com.motodigo.core.urban.repository.UrbanPlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrbanPlaceServiceTest {

	private static final UrbanPlaceRecord BASTOS = place("Bastos", 3.8960, 11.5100, "NEIGHBORHOOD", 10);
	private static final UrbanPlaceRecord NLONGKAK = place("Nlongkak", 3.8850, 11.5120, "NEIGHBORHOOD", 20);
	private static final UrbanPlaceRecord MARCHE = place("Marché Mokolo", 3.8780, 11.5030, "POI", 200);
	private static final UrbanPlaceRecord AEROPORT = place("Aéroport de Nsimalen", 3.7220, 11.5530, "POI", 230);

	@Mock
	private UrbanPlaceRepository urbanPlaceRepository;

	private UrbanPlaceService urbanPlaceService;

	@BeforeEach
	void setUp() {
		GeoProperties properties = new GeoProperties(3, 5, 60, 24, 2, 10, 3.72, 4.05, 11.38, 11.64,
				"./data/gis/routes_yaounde.gpkg");
		this.urbanPlaceService = new UrbanPlaceService(this.urbanPlaceRepository, properties);
	}

	@Test
	void popularReturnsSeedNeighborhoods() {
		when(this.urbanPlaceRepository.findPopular()).thenReturn(List.of(BASTOS, NLONGKAK));

		var popular = this.urbanPlaceService.popular();

		assertThat(popular).isNotEmpty();
		assertThat(popular).extracting(place -> place.kind()).containsOnly("NEIGHBORHOOD");
		assertThat(popular).extracting(place -> place.label()).contains("Bastos");
	}

	@Test
	void geocodeFindsBastosByPrefixAndAccent() {
		when(this.urbanPlaceRepository.findAllInServiceArea())
				.thenReturn(List.of(BASTOS, NLONGKAK, MARCHE, AEROPORT));

		var matches = this.urbanPlaceService.geocode("Bastos");

		assertThat(matches).isNotEmpty();
		assertThat(matches.getFirst().label()).isEqualTo("Bastos");
		assertThat(this.urbanPlaceService.geocode("basto")).extracting(place -> place.label()).contains("Bastos");
		assertThat(this.urbanPlaceService.geocode("marché")).extracting(place -> place.label())
				.contains("Marché Mokolo");
	}

	@Test
	void geocodeFindsNkolbissonWithoutSpacesOrHyphens() {
		UrbanPlaceRecord nkolbisson = place("Nkolbisson", 3.8750, 11.4580, "NEIGHBORHOOD", 125);
		when(this.urbanPlaceRepository.findAllInServiceArea())
				.thenReturn(List.of(BASTOS, nkolbisson));

		assertThat(this.urbanPlaceService.geocode("Nkolbisson")).extracting(place -> place.label())
				.contains("Nkolbisson");
		assertThat(this.urbanPlaceService.geocode("nkol bisson")).extracting(place -> place.label())
				.contains("Nkolbisson");
		assertThat(this.urbanPlaceService.geocode("Nkol-Bisson")).extracting(place -> place.label())
				.contains("Nkolbisson");
	}

	@Test
	void geocodeFiltersCitiesByKind() {
		UrbanPlaceRecord yaounde = place("Yaoundé", 3.8667, 11.5167, "CITY", 10);
		UrbanPlaceRecord douala = place("Douala", 4.0511, 9.7679, "CITY", 20);
		when(this.urbanPlaceRepository.findByKind("CITY")).thenReturn(List.of(yaounde, douala));

		assertThat(this.urbanPlaceService.geocode("yao", "CITY")).extracting(place -> place.label())
				.contains("Yaoundé");
		assertThat(this.urbanPlaceService.geocode("dou", "CITY")).extracting(place -> place.label())
				.contains("Douala");
	}

	@Test
	void reverseReturnsNearestSeedToBastos() {
		when(this.urbanPlaceRepository.findAllInServiceArea())
				.thenReturn(List.of(BASTOS, NLONGKAK, MARCHE, AEROPORT));

		var nearest = this.urbanPlaceService.reverse(3.8962, 11.5101);

		assertThat(nearest.label()).isEqualTo("Bastos");
	}

	private static UrbanPlaceRecord place(String label, double lat, double lon, String kind, int sortOrder) {
		return new UrbanPlaceRecord(UUID.randomUUID(), label, lat, lon, kind, true, sortOrder);
	}
}
