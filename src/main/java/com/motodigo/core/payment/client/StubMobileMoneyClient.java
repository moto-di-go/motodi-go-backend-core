package com.motodigo.core.payment.client;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "motodigo.payment.mobile-money", name = "mode", havingValue = "stub",
		matchIfMissing = true)
public class StubMobileMoneyClient implements MobileMoneyClient {

	@Override
	public MobileMoneyCollectionResult requestToPay(MobileMoneyCollectionRequest request) {
		String externalReference = "stub-" + request.provider().callbackPathSegment() + "-" + UUID.randomUUID();
		return new MobileMoneyCollectionResult(externalReference);
	}
}
