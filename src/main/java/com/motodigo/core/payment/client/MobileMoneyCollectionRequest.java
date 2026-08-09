package com.motodigo.core.payment.client;

import java.math.BigDecimal;
import java.util.UUID;

import com.motodigo.core.payment.entity.PaymentProvider;

public record MobileMoneyCollectionRequest(
		UUID paymentTransactionId,
		PaymentProvider provider,
		BigDecimal amount,
		String currency,
		String payerPhone,
		String callbackUrl) {
}
