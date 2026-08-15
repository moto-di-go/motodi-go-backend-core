package com.motodigo.core.geo.exception;

public class GeoException extends RuntimeException {

	private final GeoErrorCode errorCode;

	public GeoException(GeoErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public GeoException(GeoErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public GeoErrorCode errorCode() {
		return this.errorCode;
	}
}
