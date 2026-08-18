package com.motodigo.core.payment.client;

public interface MobileMoneyClient {

	MobileMoneyCollectionResult requestToPay(MobileMoneyCollectionRequest request);
}
