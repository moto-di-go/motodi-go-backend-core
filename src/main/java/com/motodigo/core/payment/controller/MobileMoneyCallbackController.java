package com.motodigo.core.payment.controller;

import com.motodigo.core.payment.dto.MobileMoneyCallbackRequest;
import com.motodigo.core.payment.entity.PaymentProvider;
import com.motodigo.core.payment.exception.PaymentErrorCode;
import com.motodigo.core.payment.exception.PaymentException;
import com.motodigo.core.payment.service.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/mobile-money")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class MobileMoneyCallbackController {

	private final PaymentService paymentService;

	MobileMoneyCallbackController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping("/callback")
	ResponseEntity<?> legacyCallback(@RequestBody MobileMoneyCallbackRequest body,
			@RequestHeader(value = "X-Signature", required = false) String signature,
			@RequestHeader(value = "X-Provider", required = false) String providerHeader) {
		PaymentProvider provider = resolveLegacyProvider(providerHeader);
		return ResponseEntity.ok(this.paymentService.handleCallback(provider, body, signature));
	}

	@PostMapping("/{provider}/callback")
	ResponseEntity<?> providerCallback(@PathVariable String provider,
			@RequestBody MobileMoneyCallbackRequest body,
			@RequestHeader(value = "X-Signature", required = false) String signature) {
		PaymentProvider paymentProvider;
		try {
			paymentProvider = PaymentProvider.fromCallbackPath(provider);
		}
		catch (IllegalArgumentException ex) {
			throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		}
		return ResponseEntity.ok(this.paymentService.handleCallback(paymentProvider, body, signature));
	}

	private static PaymentProvider resolveLegacyProvider(String providerHeader) {
		if (providerHeader == null || providerHeader.isBlank()) {
			return PaymentProvider.AGGREGATOR;
		}
		try {
			return PaymentProvider.fromCallbackPath(providerHeader);
		}
		catch (IllegalArgumentException ex) {
			throw new PaymentException(PaymentErrorCode.PAYMENT_VALIDATION_FAILED);
		}
	}
}
