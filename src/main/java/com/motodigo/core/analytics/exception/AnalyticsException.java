package com.motodigo.core.analytics.exception;

public class AnalyticsException extends RuntimeException {

	private final AnalyticsErrorCode errorCode;

	public AnalyticsException(AnalyticsErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public AnalyticsErrorCode errorCode() {
		return this.errorCode;
	}
}
