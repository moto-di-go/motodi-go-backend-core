package com.motodigo.core.auth.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {
	PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Ce numero de telephone est deja inscrit."),
	INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "Numero de telephone camerounais invalide."),
	OTP_INVALID(HttpStatus.BAD_REQUEST, "Code OTP invalide ou expire."),
	OTP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Trop de demandes OTP. Veuillez patienter."),
	OTP_DELIVERY_FAILED(HttpStatus.BAD_GATEWAY, "Impossible d'envoyer le code OTP."),
	AUTH_INVALID(HttpStatus.UNAUTHORIZED, "Identifiants invalides."),
	SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Session expiree."),
	PASSWORD_RESET_INVALID(HttpStatus.BAD_REQUEST, "Demande de recuperation invalide."),
	PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Ce type de profil existe deja pour cet utilisateur."),
	PROFILE_RULE_VIOLATION(HttpStatus.BAD_REQUEST, "Regle de profil invalide."),
	PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Profil introuvable."),
	PROFILE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Action non autorisee pour ce profil."),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Requete invalide.");

	private final HttpStatus status;
	private final String message;

	AuthErrorCode(HttpStatus status, String message) {
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
