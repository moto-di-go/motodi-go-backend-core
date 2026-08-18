package com.motodigo.core.pricing.exception;

public class PricingException extends RuntimeException {

	private final PricingErrorCode errorCode;

	public PricingException(PricingErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public PricingException(PricingErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public PricingErrorCode errorCode() {
		return this.errorCode;
	}
}
