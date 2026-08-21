package com.motodigo.core.notification.exception;

import org.springframework.http.HttpStatus;

public enum NotificationErrorCode {
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification introuvable."),
	NOTIFICATION_FORBIDDEN(HttpStatus.FORBIDDEN, "Acces a cette notification refuse."),
	DEVICE_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Jeton appareil invalide.");

	private final HttpStatus status;
	private final String message;

	NotificationErrorCode(HttpStatus status, String message) {
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
