package com.motodigo.core.intercity.exception;

public class IntercityException extends RuntimeException {

	private final IntercityErrorCode errorCode;

	public IntercityException(IntercityErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public IntercityException(IntercityErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public IntercityErrorCode errorCode() {
		return this.errorCode;
	}
}
