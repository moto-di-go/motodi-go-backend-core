package com.motodigo.core.offline.exception;

import org.springframework.http.HttpStatus;

public enum OfflineErrorCode {
	OFFLINE_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Requete de synchronisation invalide."),
	OFFLINE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acces offline refuse.");

	private final HttpStatus status;
	private final String message;

	OfflineErrorCode(HttpStatus status, String message) {
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
