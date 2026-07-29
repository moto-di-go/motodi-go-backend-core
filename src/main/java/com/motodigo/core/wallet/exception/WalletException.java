package com.motodigo.core.wallet.exception;

public class WalletException extends RuntimeException {

	private final WalletErrorCode errorCode;

	public WalletException(WalletErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public WalletErrorCode errorCode() {
		return this.errorCode;
	}
}
