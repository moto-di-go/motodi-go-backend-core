package com.motodigo.core.kyc.exception;

import org.springframework.http.HttpStatus;

public enum KycErrorCode {
	DOCUMENT_INVALID(HttpStatus.BAD_REQUEST, "kyc.error.document-invalid", "Document invalide."),
	DOCUMENT_TOO_LARGE(HttpStatus.BAD_REQUEST, "kyc.error.document-too-large", "Document trop volumineux."),
	DOCUMENT_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "kyc.error.document-type-not-allowed", "Type de document non autorise pour ce profil."),
	DOCUMENT_MIME_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "kyc.error.document-mime-not-allowed", "Format de document non autorise."),
	DOCUMENT_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST, "kyc.error.document-signature-invalid", "Signature de fichier incoherente."),
	DOCUMENT_DUPLICATE(HttpStatus.CONFLICT, "kyc.error.document-duplicate", "Ce document a deja ete televerse."),
	DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "kyc.error.document-not-found", "Document introuvable."),
	KYC_PROFILE_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "kyc.error.profile-not-eligible", "Profil non eligible au KYC documentaire."),
	KYC_ACCESS_DENIED(HttpStatus.FORBIDDEN, "kyc.error.access-denied", "Acces KYC refuse."),
	KYC_STORAGE_FAILED(HttpStatus.BAD_GATEWAY, "kyc.error.storage-failed", "Stockage du document impossible."),
	KYC_CRYPTO_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "kyc.error.crypto-failed", "Traitement chiffre du document impossible."),
	KYC_CONFIGURATION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "kyc.error.configuration-invalid", "Configuration KYC invalide.");

	private final HttpStatus status;
	private final String messageKey;
	private final String defaultMessage;

	KycErrorCode(HttpStatus status, String messageKey, String defaultMessage) {
		this.status = status;
		this.messageKey = messageKey;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return this.status;
	}

	public String messageKey() {
		return this.messageKey;
	}

	public String defaultMessage() {
		return this.defaultMessage;
	}
}
