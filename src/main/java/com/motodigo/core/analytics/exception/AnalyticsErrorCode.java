package com.motodigo.core.analytics.exception;

import org.springframework.http.HttpStatus;

public enum AnalyticsErrorCode {
	ANALYTICS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acces KPI refuse."),
	ANALYTICS_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Requete KPI invalide.");

	private final HttpStatus status;
	private final String message;

	AnalyticsErrorCode(HttpStatus status, String message) {
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
