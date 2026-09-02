package com.motodigo.core.offline.exception;

public class OfflineException extends RuntimeException {

	private final OfflineErrorCode errorCode;

	public OfflineException(OfflineErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public OfflineException(OfflineErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public OfflineErrorCode errorCode() {
		return this.errorCode;
	}
}
