package com.motodigo.core.wallet.exception;

import org.springframework.http.HttpStatus;

public enum WalletErrorCode {
	WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "Portefeuille introuvable."),
	WALLET_NOT_ACTIVE(HttpStatus.CONFLICT, "Le portefeuille n'est pas actif."),
	INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "Solde insuffisant pour cette operation."),
	WALLET_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Requete portefeuille invalide.");

	private final HttpStatus status;
	private final String message;

	WalletErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return this.status;
	}

	public String message() {
		return this.message;
	}
}
