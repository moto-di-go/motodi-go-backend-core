package com.motodigo.core.notification.exception;

public class NotificationException extends RuntimeException {

	private final NotificationErrorCode errorCode;

	public NotificationException(NotificationErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public NotificationErrorCode errorCode() {
		return this.errorCode;
	}
}
