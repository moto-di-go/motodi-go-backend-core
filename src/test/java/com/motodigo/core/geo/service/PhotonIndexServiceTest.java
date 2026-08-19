package com.motodigo.core.geo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.motodigo.core.geo.client.PhotonClient;
import com.motodigo.core.geo.client.PhotonControlClient;
import com.motodigo.core.geo.client.PhotonControlClient.ControlStatus;
import com.motodigo.core.geo.config.GeoStackProperties;
import com.motodigo.core.geo.exception.GeoErrorCode;
import com.motodigo.core.geo.exception.GeoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotonIndexServiceTest {

	@Mock
	private PhotonClient photonClient;

	@Mock
	private PhotonControlClient photonControlClient;

	private PhotonIndexService service;

	@BeforeEach
	void setUp() {
		GeoStackProperties properties = new GeoStackProperties(true, "http://photon:2322", 1200, false,
				"http://valhalla:8002", 2500, "motorcycle", 2, "http://photon-control:8080", 5000);
		this.service = new PhotonIndexService(properties, this.photonClient, this.photonControlClient);
	}

	@Test
	void statusMergesPhotonHealthAndControlState() {
		when(this.photonClient.isReachable()).thenReturn(true);
		when(this.photonControlClient.configured()).thenReturn(true);
		when(this.photonControlClient.status())
				.thenReturn(new ControlStatus(true, false, true, "Index Photon pret.", 200));

		var status = this.service.status();

		assertThat(status.enabled()).isTrue();
		assertThat(status.ready()).isTrue();
		assertThat(status.updating()).isFalse();
		assertThat(status.manualUpdateAvailable()).isTrue();
	}

	@Test
	void startUpdateRejectsWhenAlreadyRunning() {
		when(this.photonControlClient.configured()).thenReturn(true);
		when(this.photonControlClient.startUpdate())
				.thenReturn(new ControlStatus(true, true, true, "Deja en cours", 409));

		assertThatThrownBy(() -> this.service.startUpdate())
				.isInstanceOf(GeoException.class)
				.extracting(ex -> ((GeoException) ex).errorCode())
				.isEqualTo(GeoErrorCode.PHOTON_UPDATE_IN_PROGRESS);
	}

	@Test
	void startUpdateReturnsAcceptedPayload() {
		when(this.photonControlClient.configured()).thenReturn(true);
		when(this.photonControlClient.startUpdate())
				.thenReturn(new ControlStatus(true, true, true, "Telechargement lance.", 202));

		var response = this.service.startUpdate();

		assertThat(response.updating()).isTrue();
		assertThat(response.message()).contains("Telechargement");
	}
}
