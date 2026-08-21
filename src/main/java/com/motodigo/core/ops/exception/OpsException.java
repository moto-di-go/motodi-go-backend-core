package com.motodigo.core.ops.exception;

public class OpsException extends RuntimeException {

	private final OpsErrorCode errorCode;

	public OpsException(OpsErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public OpsErrorCode errorCode() {
		return this.errorCode;
	}
}
