package com.motodigo.core.kyc.exception;

import org.springframework.http.HttpStatus;

public enum KycErrorCode {
	KYC_PROFILE_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "Profil non eligible au KYC documentaire."),
	KYC_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acces KYC refuse."),
	KYC_DOCUMENT_INVALID(HttpStatus.BAD_REQUEST, "Document KYC invalide."),
	KYC_DOCUMENT_DUPLICATE(HttpStatus.CONFLICT, "Ce fichier a déjà été téléversé. Choisissez-en un autre."),
	KYC_DECISION_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Un motif est obligatoire pour rejeter un profil KYC."),
	KYC_DECISION_NOT_ALLOWED(HttpStatus.CONFLICT, "Decision KYC impossible pour ce profil."),
	KYC_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Document KYC introuvable."),
	KYC_STORAGE_FAILED(HttpStatus.BAD_GATEWAY, "Lecture du document KYC impossible."),
	DOCUMENT_INVALID(HttpStatus.BAD_REQUEST, "Document KYC invalide."),
	DOCUMENT_TOO_LARGE(HttpStatus.BAD_REQUEST, "Document KYC trop volumineux."),
	DOCUMENT_MIME_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Type de document KYC non autorise."),
	DOCUMENT_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST, "Signature du document KYC invalide."),
	KYC_CONFIGURATION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "Configuration KYC invalide."),
	KYC_CRYPTO_FAILED(HttpStatus.BAD_GATEWAY, "Traitement cryptographique KYC impossible.");

	private final HttpStatus status;
	private final String message;

	KycErrorCode(HttpStatus status, String message) {
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
