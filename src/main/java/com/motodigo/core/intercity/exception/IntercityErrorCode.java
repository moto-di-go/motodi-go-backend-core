package com.motodigo.core.intercity.exception;

import org.springframework.http.HttpStatus;

public enum IntercityErrorCode {
	ANNOUNCEMENT_INVALID(HttpStatus.BAD_REQUEST, "Annonce incomplète ou incohérente."),
	ANNOUNCEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "Profil non autorisé à gérer des annonces interurbaines."),
	ANNOUNCEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Annonce introuvable."),
	ANNOUNCEMENT_NOT_MODIFIABLE(HttpStatus.CONFLICT, "Annonce non modifiable dans son état actuel."),
	VEHICLE_INVALID(HttpStatus.BAD_REQUEST, "Véhicule invalide, non validé ou non possédé."),
	ACTIVE_PROFILE_REQUIRED(HttpStatus.FORBIDDEN, "Un profil actif transporteur ou agence est requis.");

	private final HttpStatus status;
	private final String message;

	IntercityErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return this.status;
	}

	public String message() {
		return this.message;
	}
}
