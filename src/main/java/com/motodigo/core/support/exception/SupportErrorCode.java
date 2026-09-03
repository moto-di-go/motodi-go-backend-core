package com.motodigo.core.support.exception;

import org.springframework.http.HttpStatus;

public enum SupportErrorCode {
	SUPPORT_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Demande support invalide."),
	SUPPORT_TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "Ticket support introuvable."),
	SUPPORT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acces support refuse."),
	MODERATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acces moderation refuse."),
	MODERATION_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Action de moderation invalide."),
	MODERATION_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "Cible de moderation introuvable.");

	private final HttpStatus status;
	private final String message;

	SupportErrorCode(HttpStatus status, String message) {
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
