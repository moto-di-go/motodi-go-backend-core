package com.motodigo.core.tracking.exception;

import org.springframework.http.HttpStatus;

public enum TrackingErrorCode {
	TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "Trajet introuvable."),
	TRIP_FORBIDDEN(HttpStatus.FORBIDDEN, "Profil non autorise pour ce trajet."),
	INVALID_TRIP_TRANSITION(HttpStatus.CONFLICT, "Transition de statut de trajet non autorisee."),
	TRIP_NOT_COMPLETABLE(HttpStatus.CONFLICT, "Ce trajet ne peut pas etre termine."),
	TRIP_ALREADY_EXISTS(HttpStatus.CONFLICT, "Un trajet existe deja pour cette reservation."),
	BOOKING_NOT_PAID(HttpStatus.CONFLICT, "La reservation doit etre payee pour creer un trajet."),
	ACTIVE_PROFILE_REQUIRED(HttpStatus.FORBIDDEN, "Un profil actif est requis."),
	TRIP_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Demande de suivi de trajet invalide.");

	private final HttpStatus status;
	private final String message;

	TrackingErrorCode(HttpStatus status, String message) {
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
