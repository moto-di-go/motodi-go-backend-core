package com.motodigo.core.tracking.exception;

public class TrackingException extends RuntimeException {

	private final TrackingErrorCode errorCode;

	public TrackingException(TrackingErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public TrackingException(TrackingErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public TrackingErrorCode errorCode() {
		return this.errorCode;
	}
}
