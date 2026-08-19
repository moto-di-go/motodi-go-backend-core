package com.motodigo.core.geo.service;

import com.motodigo.core.geo.client.PhotonClient;
import com.motodigo.core.geo.client.PhotonControlClient;
import com.motodigo.core.geo.client.PhotonControlClient.ControlStatus;
import com.motodigo.core.geo.config.GeoStackProperties;
import com.motodigo.core.geo.dto.PhotonIndexStatusResponse;
import com.motodigo.core.geo.exception.GeoErrorCode;
import com.motodigo.core.geo.exception.GeoException;
import org.springframework.stereotype.Service;

@Service
public class PhotonIndexService {

	private final GeoStackProperties properties;
	private final PhotonClient photonClient;
	private final PhotonControlClient photonControlClient;

	public PhotonIndexService(GeoStackProperties properties, PhotonClient photonClient,
			PhotonControlClient photonControlClient) {
		this.properties = properties;
		this.photonClient = photonClient;
		this.photonControlClient = photonControlClient;
	}

	public PhotonIndexStatusResponse status() {
		if (!this.properties.photonEnabled()) {
			return new PhotonIndexStatusResponse(false, false, false, false,
					"Photon n'est pas active sur cet environnement.");
		}
		boolean ready = this.photonClient.isReachable();
		if (!this.photonControlClient.configured()) {
			return new PhotonIndexStatusResponse(true, ready, false, false,
					ready
							? "Photon repond. Le bouton de mise a jour n'est disponible que si photon-control tourne."
							: "Photon ne repond pas.");
		}
		ControlStatus control = this.photonControlClient.status();
		boolean updating = control.updating();
		String message = control.message();
		if (message == null || message.isBlank()) {
			message = ready ? "Index Photon pret." : "Photon ne repond pas.";
		}
		return new PhotonIndexStatusResponse(true, ready || control.ready(), updating,
				control.manualUpdateAvailable(), message);
	}

	public PhotonIndexStatusResponse startUpdate() {
		if (!this.properties.photonEnabled()) {
			throw new GeoException(GeoErrorCode.PHOTON_DISABLED);
		}
		if (!this.photonControlClient.configured()) {
			throw new GeoException(GeoErrorCode.PHOTON_UPDATE_UNAVAILABLE);
		}
		ControlStatus control = this.photonControlClient.startUpdate();
		if (control.httpStatus() == 409) {
			throw new GeoException(GeoErrorCode.PHOTON_UPDATE_IN_PROGRESS,
					control.message() == null ? GeoErrorCode.PHOTON_UPDATE_IN_PROGRESS.message() : control.message());
		}
		if (control.httpStatus() >= 400 || !control.manualUpdateAvailable() && control.httpStatus() != 202) {
			throw new GeoException(GeoErrorCode.PHOTON_UPDATE_UNAVAILABLE,
					control.message() == null ? GeoErrorCode.PHOTON_UPDATE_UNAVAILABLE.message() : control.message());
		}
		String message = control.message() == null
				? "Mise a jour Photon lancee en arriere-plan."
				: control.message();
		return new PhotonIndexStatusResponse(true, control.ready(), true, true, message);
	}
}
