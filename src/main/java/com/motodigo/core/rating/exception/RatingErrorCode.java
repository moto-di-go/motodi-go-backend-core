package com.motodigo.core.rating.exception;

import org.springframework.http.HttpStatus;

public enum RatingErrorCode {
	RATING_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Demande de notation invalide."),
	RATING_FORBIDDEN(HttpStatus.FORBIDDEN, "Notation non autorisee pour ce trajet."),
	TRIP_NOT_COMPLETED(HttpStatus.FORBIDDEN, "Le trajet doit etre termine pour noter."),
	TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "Trajet introuvable."),
	RATING_ALREADY_EXISTS(HttpStatus.CONFLICT, "Une notation existe deja pour ce trajet."),
	RATED_PROFILE_INVALID(HttpStatus.BAD_REQUEST, "Profil evalue invalide.");

	private final HttpStatus status;
	private final String message;

	RatingErrorCode(HttpStatus status, String message) {
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
