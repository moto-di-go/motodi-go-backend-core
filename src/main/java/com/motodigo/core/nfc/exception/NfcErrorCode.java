package com.motodigo.core.nfc.exception;

import org.springframework.http.HttpStatus;

public enum NfcErrorCode {
	NFC_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Requete NFC invalide."),
	NFC_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Paiement NFC indisponible."),
	NFC_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Jeton NFC introuvable."),
	NFC_TOKEN_EXPIRED(HttpStatus.CONFLICT, "Jeton NFC expire."),
	NFC_TOKEN_ALREADY_CONSUMED(HttpStatus.CONFLICT, "Jeton NFC deja consomme."),
	NFC_INVALID_SIGNATURE(HttpStatus.CONFLICT, "Signature NFC invalide."),
	NFC_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "Montant NFC en conflit."),
	NFC_TRIP_MISMATCH(HttpStatus.CONFLICT, "Trajet NFC en conflit."),
	NFC_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acces NFC refuse."),
	NFC_INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "Solde disponible insuffisant pour NFC.");

	private final HttpStatus status;
	private final String message;

	NfcErrorCode(HttpStatus status, String message) {
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
