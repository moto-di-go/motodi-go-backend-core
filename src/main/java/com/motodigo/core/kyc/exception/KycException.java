package com.motodigo.core.kyc.exception;

public class KycException extends RuntimeException {

	private final KycErrorCode errorCode;

	public KycException(KycErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public KycException(KycErrorCode errorCode, Throwable cause) {
		super(errorCode.message(), cause);
		this.errorCode = errorCode;
	}

	public KycErrorCode errorCode() {
		return this.errorCode;
	}
}
