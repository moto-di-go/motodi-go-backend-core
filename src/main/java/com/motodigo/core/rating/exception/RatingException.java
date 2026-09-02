package com.motodigo.core.rating.exception;

public class RatingException extends RuntimeException {

	private final RatingErrorCode errorCode;

	public RatingException(RatingErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public RatingException(RatingErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public RatingErrorCode errorCode() {
		return this.errorCode;
	}
}
