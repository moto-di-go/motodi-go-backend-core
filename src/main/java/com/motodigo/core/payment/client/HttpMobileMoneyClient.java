package com.motodigo.core.payment.client;

import java.time.Duration;
import java.util.Map;

import com.motodigo.core.payment.config.PaymentProperties;
import com.motodigo.core.payment.exception.PaymentErrorCode;
import com.motodigo.core.payment.exception.PaymentException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "motodigo.payment.mobile-money", name = "mode", havingValue = "http")
public class HttpMobileMoneyClient implements MobileMoneyClient {

	private final PaymentProperties properties;
	private final RestClient.Builder restClientBuilder;

	public HttpMobileMoneyClient(PaymentProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(properties.mobileMoney().connectTimeoutMs()));
		requestFactory.setReadTimeout(Duration.ofMillis(properties.mobileMoney().readTimeoutMs()));
		this.restClientBuilder = RestClient.builder().requestFactory(requestFactory);
	}

	HttpMobileMoneyClient(PaymentProperties properties, RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.restClientBuilder = restClientBuilder;
	}

	@Override
	@SuppressWarnings("unchecked")
	public MobileMoneyCollectionResult requestToPay(MobileMoneyCollectionRequest request) {
		PaymentProperties.Provider providerConfig = this.properties.mobileMoney()
				.provider(request.provider().name());
		if (providerConfig == null) {
			throw new PaymentException(PaymentErrorCode.DEPOSIT_INIT_FAILED);
		}

		Map<String, Object> payload = Map.of(
				"paymentTransactionId", request.paymentTransactionId().toString(),
				"amount", request.amount(),
				"currency", request.currency(),
				"payerPhone", request.payerPhone(),
				"callbackUrl", request.callbackUrl());

		try {
			Map<String, Object> response = this.restClientBuilder
					.baseUrl(providerConfig.baseUrl())
					.build()
					.post()
					.uri("/collections/requesttopay")
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.headers(headers -> {
						if (!providerConfig.apiKey().isBlank()) {
							headers.setBearerAuth(providerConfig.apiKey());
						}
						if (!providerConfig.subscriptionKey().isBlank()) {
							headers.set("Ocp-Apim-Subscription-Key", providerConfig.subscriptionKey());
						}
					})
					.body(payload)
					.retrieve()
					.body(Map.class);
			if (response == null || response.get("externalReference") == null
					|| response.get("externalReference").toString().isBlank()) {
				throw new PaymentException(PaymentErrorCode.DEPOSIT_INIT_FAILED);
			}
			return new MobileMoneyCollectionResult(response.get("externalReference").toString());
		}
		catch (PaymentException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new PaymentException(PaymentErrorCode.DEPOSIT_INIT_FAILED);
		}
	}
}
