package com.motodigo.core.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MobileMoneyCallbackRequest(
		String externalReference,
		String status,
		String failureReason) {
}
