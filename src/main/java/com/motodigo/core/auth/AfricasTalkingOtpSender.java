package com.motodigo.core.auth;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.motodigo.core.auth.config.AfricasTalkingProperties;
import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import com.motodigo.core.auth.service.OtpSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "motodigo.otp", name = "provider", havingValue = "africas-talking")
public class AfricasTalkingOtpSender implements OtpSender {

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
		if (this.properties.username().isBlank() || this.properties.apiKey().isBlank()
				|| this.properties.senderId().isBlank()) {
			throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED);
		}

		Map<String, Object> payload = Map.of(
				"username", this.properties.username(),
				"phoneNumbers", List.of(phoneNumber),
				"message", message,
				"senderId", this.properties.senderId(),
				"enqueue", this.properties.enqueue());

		Map<String, Object> response;
		try {
			response = this.restClient.post()
					.uri("/version1/messaging/bulk")
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.header("apiKey", this.properties.apiKey())
					.body(payload)
					.retrieve()
					.body(Map.class);
		}
		catch (RuntimeException ex) {
			throw new AuthException(AuthErrorCode.OTP_DELIVERY_FAILED);
		}

		if (!isSuccessful(response)) {
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

	@SuppressWarnings("unchecked")
	private static boolean isSuccessful(Map<String, Object> response) {
		if (response == null || !(response.get("SMSMessageData") instanceof Map<?, ?> smsData)) {
			return false;
		}
		Object recipientsValue = smsData.get("Recipients");
		if (!(recipientsValue instanceof List<?> recipients) || recipients.isEmpty()) {
			return false;
		}
		for (Object recipientValue : recipients) {
			if (!(recipientValue instanceof Map<?, ?> recipient)) {
				return false;
			}
			Object statusCode = recipient.get("statusCode");
			int code = statusCode instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(statusCode));
			if (code != 100 && code != 101 && code != 102) {
				return false;
			}
		}
		return true;
	}
}
