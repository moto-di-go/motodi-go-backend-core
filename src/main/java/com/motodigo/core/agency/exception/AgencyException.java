package com.motodigo.core.agency.exception;

public class AgencyException extends RuntimeException {

	private final AgencyErrorCode errorCode;

	public AgencyException(AgencyErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public AgencyErrorCode errorCode() {
		return this.errorCode;
	}
}
