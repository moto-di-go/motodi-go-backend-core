package com.motodigo.core.nfc.exception;

public class NfcException extends RuntimeException {

	private final NfcErrorCode errorCode;

	public NfcException(NfcErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public NfcException(NfcErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public NfcErrorCode errorCode() {
		return this.errorCode;
	}
}
