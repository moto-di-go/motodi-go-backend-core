package com.motodigo.core.support.exception;

public class SupportException extends RuntimeException {

	private final SupportErrorCode errorCode;

	public SupportException(SupportErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public SupportException(SupportErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public SupportErrorCode errorCode() {
		return this.errorCode;
	}
}
