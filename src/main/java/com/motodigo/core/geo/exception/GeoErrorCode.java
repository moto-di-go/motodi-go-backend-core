package com.motodigo.core.geo.exception;

import org.springframework.http.HttpStatus;

public enum GeoErrorCode {
	DRIVER_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Profil conducteur non autorise a publier une position."),
	ACTIVE_PROFILE_REQUIRED(HttpStatus.FORBIDDEN, "Un profil mototaxi actif est requis."),
	GPS_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "Le consentement GPS est requis pour diffuser la position."),
	DRIVER_OFFLINE(HttpStatus.CONFLICT, "Le conducteur doit etre disponible ou occupe pour diffuser sa position."),
	POSITION_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "Frequence GPS trop elevee. Intervalle minimum: 3 secondes."),
	POSITION_INVALID(HttpStatus.BAD_REQUEST, "Coordonnees GPS invalides."),
	POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "Aucune position recente pour ce conducteur."),
	AVAILABILITY_INVALID(HttpStatus.BAD_REQUEST, "Disponibilite conducteur invalide."),
	OUTSIDE_SERVICE_AREA(HttpStatus.UNPROCESSABLE_ENTITY, "Point hors de la zone de service Yaounde."),
	ROUTE_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "Aucun itineraire routier trouve a Yaounde."),
	NEARBY_RADIUS_INVALID(HttpStatus.BAD_REQUEST, "Rayon de recherche invalide.");

	private final HttpStatus status;
	private final String message;

	GeoErrorCode(HttpStatus status, String message) {
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
