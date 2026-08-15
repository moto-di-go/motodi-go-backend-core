package com.motodigo.core.pricing.exception;

import org.springframework.http.HttpStatus;

public enum PricingErrorCode {
	ESTIMATE_INVALID(HttpStatus.BAD_REQUEST, "Origine ou destination invalide pour l'estimation."),
	NO_PRICING_RULE(HttpStatus.UNPROCESSABLE_ENTITY, "Aucune regle tarifaire active pour cette zone et ce creneau."),
	OUTSIDE_SERVICE_AREA(HttpStatus.UNPROCESSABLE_ENTITY, "Itineraire hors de la zone de service Yaounde."),
	RULE_INVALID(HttpStatus.BAD_REQUEST, "Regle tarifaire invalide."),
	RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "Regle tarifaire introuvable."),
	LAYER_INVALID(HttpStatus.BAD_REQUEST, "Couche SIG invalide."),
	LAYER_NOT_FOUND(HttpStatus.NOT_FOUND, "Couche SIG introuvable."),
	ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "Un profil administrateur est requis.");

	private final HttpStatus status;
	private final String message;

	PricingErrorCode(HttpStatus status, String message) {
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
