package com.motodigo.core.auth.exception;

public class AuthException extends RuntimeException {

	private final AuthErrorCode errorCode;

	public AuthException(AuthErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public AuthErrorCode errorCode() {
		return this.errorCode;
	}
}
