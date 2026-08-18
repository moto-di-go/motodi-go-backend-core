package com.motodigo.core.payment.exception;

public class PaymentException extends RuntimeException {

	private final PaymentErrorCode errorCode;

	public PaymentException(PaymentErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public PaymentErrorCode errorCode() {
		return this.errorCode;
	}
}
