package com.motodigo.core.ops.exception;

import org.springframework.http.HttpStatus;

public enum OpsErrorCode {
	OPS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acces operations refuse."),
	OPS_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Requete operations invalide."),
	OPS_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Utilisateur introuvable.");

	private final HttpStatus status;
	private final String message;

	OpsErrorCode(HttpStatus status, String message) {
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
