package com.motodigo.core.payment.entity;

public enum PaymentStatus {
	INITIATED,
	PENDING_CALLBACK,
	CONFIRMED,
	FAILED,
	EXPIRED,
	REFUNDED;

	public boolean isTerminal() {
		return this == CONFIRMED || this == FAILED || this == EXPIRED || this == REFUNDED;
	}
}
