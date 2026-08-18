package com.motodigo.core.booking.exception;

import org.springframework.http.HttpStatus;

public enum BookingErrorCode {
	BOOKING_NOT_AVAILABLE(HttpStatus.CONFLICT, "Annonce indisponible ou complete pour cette souscription."),
	NO_SEAT_AVAILABLE(HttpStatus.CONFLICT, "Places insuffisantes pour confirmer cette reservation."),
	BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Reservation introuvable."),
	BOOKING_FORBIDDEN(HttpStatus.FORBIDDEN, "Profil non autorise pour cette operation de reservation."),
	BOOKING_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Cette reservation ne peut pas etre annulee."),
	BOOKING_NOT_CONFIRMABLE(HttpStatus.CONFLICT, "Cette reservation ne peut pas etre confirmee."),
	BOOKING_DUPLICATE(HttpStatus.CONFLICT, "Une reservation active existe deja pour cette annonce."),
	BOOKING_OWN_ANNOUNCEMENT(HttpStatus.FORBIDDEN, "Vous ne pouvez pas souscrire à votre propre annonce."),
	BOOKING_KYC_REQUIRED(HttpStatus.FORBIDDEN, "Validation KYC requise (CNI) avant de souscrire a un trajet."),
	ACTIVE_PROFILE_REQUIRED(HttpStatus.FORBIDDEN, "Un profil actif est requis."),
	BOOKING_INVALID(HttpStatus.BAD_REQUEST, "Demande de reservation invalide.");

	private final HttpStatus status;
	private final String message;

	BookingErrorCode(HttpStatus status, String message) {
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
