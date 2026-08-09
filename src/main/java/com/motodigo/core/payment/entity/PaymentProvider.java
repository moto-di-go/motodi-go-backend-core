package com.motodigo.core.payment.entity;

public enum PaymentProvider {
	MTN_MOMO,
	ORANGE_MONEY,
	AGGREGATOR,
	WALLET,
	NFC;

	public static PaymentProvider forDeposit(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("provider required");
		}
		PaymentProvider provider = PaymentProvider.valueOf(value.trim().toUpperCase());
		if (provider != MTN_MOMO && provider != ORANGE_MONEY && provider != AGGREGATOR) {
			throw new IllegalArgumentException("unsupported deposit provider");
		}
		return provider;
	}

	public static PaymentProvider fromCallbackPath(String pathSegment) {
		if (pathSegment == null || pathSegment.isBlank()) {
			return AGGREGATOR;
		}
		return switch (pathSegment.trim().toLowerCase()) {
			case "mtn" -> MTN_MOMO;
			case "orange" -> ORANGE_MONEY;
			case "aggregator", "callback" -> AGGREGATOR;
			default -> throw new IllegalArgumentException("unknown callback provider");
		};
	}

	public String callbackPathSegment() {
		return switch (this) {
			case MTN_MOMO -> "mtn";
			case ORANGE_MONEY -> "orange";
			case AGGREGATOR -> "aggregator";
			default -> throw new IllegalStateException("not a mobile money provider");
		};
	}
}
