package com.motodigo.core.payment.exception;

import org.springframework.http.HttpStatus;

public enum PaymentErrorCode {
	DEPOSIT_NOT_FOUND(HttpStatus.NOT_FOUND, "Depot introuvable."),
	DEPOSIT_INIT_FAILED(HttpStatus.BAD_GATEWAY, "Impossible d'initier le depot Mobile Money."),
	PAYMENT_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Requete de paiement invalide."),
	PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acces paiement refuse."),
	CALLBACK_TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction introuvable pour ce callback."),
	WALLET_NOT_ACTIVE(HttpStatus.CONFLICT, "Le portefeuille n'est pas actif."),
	INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "Solde insuffisant pour payer cette reservation."),
	RIDE_PAYMENT_FAILED(HttpStatus.CONFLICT, "Le paiement du trajet a echoue."),
	RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Recu introuvable.");

	private final HttpStatus status;
	private final String message;

	PaymentErrorCode(HttpStatus status, String message) {
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
