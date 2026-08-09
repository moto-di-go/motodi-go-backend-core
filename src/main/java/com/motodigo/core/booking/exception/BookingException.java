package com.motodigo.core.booking.exception;

public class BookingException extends RuntimeException {

	private final BookingErrorCode errorCode;

	public BookingException(BookingErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public BookingException(BookingErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public BookingErrorCode errorCode() {
		return this.errorCode;
	}
}
