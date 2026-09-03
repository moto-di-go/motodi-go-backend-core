package com.motodigo.core.agency.exception;

import org.springframework.http.HttpStatus;

public enum AgencyErrorCode {
	AGENCY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acces espace agence refuse."),
	AGENCY_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Requete agence invalide.");

	private final HttpStatus status;
	private final String message;

	AgencyErrorCode(HttpStatus status, String message) {
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
