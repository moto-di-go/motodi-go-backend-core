package com.motodigo.core.auth;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.motodigo.core.auth.config.AfricasTalkingProperties;
import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.auth.service.OtpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "motodigo.otp", name = "provider", havingValue = "africas-talking")
public class AfricasTalkingOtpSender implements OtpSender {

	private static final Logger LOGGER = LoggerFactory.getLogger(AfricasTalkingOtpSender.class);

	private final AfricasTalkingProperties properties;
	private final RestClient restClient;

	@Autowired
	public AfricasTalkingOtpSender(AfricasTalkingProperties properties) {
		this(properties, buildRestClient(properties));
	}

	AfricasTalkingOtpSender(AfricasTalkingProperties properties, RestClient restClient) {
		this.properties = properties;
		this.restClient = restClient;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void sendOtp(String phoneNumber, String message) {
		if (this.properties.username().isBlank() || this.properties.apiKey().isBlank()) {
			LOGGER.warn("Africa's Talking OTP delivery failed: missing username or api key");
			throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED);
		}
		if (!this.properties.sandbox() && this.properties.requestFrom().isBlank()) {
			LOGGER.warn("Africa's Talking OTP delivery failed: missing sender id");
			throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED);
		}

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("username", this.properties.apiUsername());
		form.add("to", phoneNumber);
		form.add("message", message);
		String from = this.properties.requestFrom();
		if (!from.isBlank()) {
			form.add("from", from);
		}
		form.add("enqueue", this.properties.enqueue() ? "1" : "0");

		Map<String, Object> response;
		try {
			response = this.restClient.post()
					.uri("/version1/messaging")
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.accept(MediaType.APPLICATION_JSON)
					.header("apiKey", this.properties.apiKey())
					.body(form)
					.retrieve()
					.body(Map.class);
		}
		catch (RuntimeException ex) {
			LOGGER.warn("Africa's Talking OTP request failed: {}", ex.getMessage());
			throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED);
		}

		DeliveryOutcome outcome = outcomeOf(response);
		if (!outcome.delivered()) {
			LOGGER.warn("Africa's Talking OTP delivery failed: summary={}, statusCode={}, status={}",
					outcome.summary(), outcome.statusCode(), outcome.status());
			throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED);
		}
	}

	private static RestClient buildRestClient(AfricasTalkingProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
		requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
		return RestClient.builder()
				.baseUrl(properties.baseUrl().toString())
				.requestFactory(requestFactory)
				.build();
	}

	private static DeliveryOutcome outcomeOf(Map<String, Object> response) {
		if (response == null || !(response.get("SMSMessageData") instanceof Map<?, ?> smsData)) {
			return DeliveryOutcome.failed(null, null, null);
		}
		String summary = smsData.get("Message") == null ? null : String.valueOf(smsData.get("Message"));
		Object recipientsValue = smsData.get("Recipients");
		if (!(recipientsValue instanceof List<?> recipients) || recipients.isEmpty()) {
			return DeliveryOutcome.failed(summary, null, null);
		}
		for (Object recipientValue : recipients) {
			if (!(recipientValue instanceof Map<?, ?> recipient)) {
				return DeliveryOutcome.failed(summary, null, null);
			}
			Object statusCode = recipient.get("statusCode");
			int code = statusCode instanceof Number number ? number.intValue()
					: Integer.parseInt(String.valueOf(statusCode));
			String status = recipient.get("status") == null ? null : String.valueOf(recipient.get("status"));
			if (code != 100 && code != 101 && code != 102) {
				return DeliveryOutcome.failed(summary, code, status);
			}
		}
		return DeliveryOutcome.accepted();
	}

	private record DeliveryOutcome(boolean delivered, String summary, Integer statusCode, String status) {

		private static DeliveryOutcome accepted() {
			return new DeliveryOutcome(true, null, null, null);
		}

		private static DeliveryOutcome failed(String summary, Integer statusCode, String status) {
			return new DeliveryOutcome(false, summary, statusCode, status);
		}
	}
}
