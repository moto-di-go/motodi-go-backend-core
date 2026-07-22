package com.motodigo.core.kyc.exception;

import org.springframework.http.HttpStatus;

public enum KycErrorCode {
	KYC_PROFILE_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "Profil non eligible au KYC documentaire."),
	KYC_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acces KYC refuse."),
	KYC_DECISION_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Un motif est obligatoire pour rejeter un profil KYC."),
	KYC_DECISION_NOT_ALLOWED(HttpStatus.CONFLICT, "Decision KYC impossible pour ce profil."),
	KYC_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Document KYC introuvable."),
	KYC_STORAGE_FAILED(HttpStatus.BAD_GATEWAY, "Lecture du document KYC impossible.");

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
