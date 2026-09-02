package com.motodigo.core.nfc.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motodigo.core.nfc.config.NfcProperties;
import com.motodigo.core.nfc.exception.NfcErrorCode;
import com.motodigo.core.nfc.exception.NfcException;
import org.springframework.stereotype.Component;

@Component
public class NfcTokenSigner {

	private final NfcProperties properties;
	private final ObjectMapper objectMapper;

	public NfcTokenSigner(NfcProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public record SignedPayload(
			UUID jti,
			UUID walletId,
			BigDecimal amount,
			String currency,
			UUID bookingId,
			UUID tripId,
			UUID receiverWalletId,
			Instant exp,
			String payloadJson,
			String signedToken) {
	}

	public SignedPayload sign(UUID jti, UUID walletId, BigDecimal amount, String currency, UUID bookingId,
			UUID tripId, UUID receiverWalletId, Instant exp) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("jti", jti.toString());
		payload.put("walletId", walletId.toString());
		payload.put("amount", amount.toPlainString());
		payload.put("currency", currency);
		if (bookingId != null) {
			payload.put("bookingId", bookingId.toString());
		}
		if (tripId != null) {
			payload.put("tripId", tripId.toString());
		}
		if (receiverWalletId != null) {
			payload.put("receiverWalletId", receiverWalletId.toString());
		}
		payload.put("exp", exp.getEpochSecond());
		String json;
		try {
			json = this.objectMapper.writeValueAsString(payload);
		}
		catch (JsonProcessingException ex) {
			throw new NfcException(NfcErrorCode.NFC_VALIDATION_FAILED, "Impossible de serialiser le jeton NFC.");
		}
		String body = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(json.getBytes(StandardCharsets.UTF_8));
		String signature = hmac(body);
		return new SignedPayload(jti, walletId, amount, currency, bookingId, tripId, receiverWalletId, exp, json,
				body + "." + signature);
	}

	public Map<String, Object> verifyAndParse(String signedToken) {
		if (signedToken == null || !signedToken.contains(".")) {
			throw new NfcException(NfcErrorCode.NFC_INVALID_SIGNATURE);
		}
		int dot = signedToken.lastIndexOf('.');
		String body = signedToken.substring(0, dot);
		String signature = signedToken.substring(dot + 1);
		if (!constantTimeEquals(signature, hmac(body))) {
			throw new NfcException(NfcErrorCode.NFC_INVALID_SIGNATURE);
		}
		try {
			byte[] jsonBytes = Base64.getUrlDecoder().decode(body);
			@SuppressWarnings("unchecked")
			Map<String, Object> map = this.objectMapper.readValue(jsonBytes, Map.class);
			return map;
		}
		catch (Exception ex) {
			throw new NfcException(NfcErrorCode.NFC_INVALID_SIGNATURE);
		}
	}

	private String hmac(String body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(this.properties.signingSecret().getBytes(StandardCharsets.UTF_8),
					"HmacSHA256"));
			return Base64.getUrlEncoder().withoutPadding()
					.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new NfcException(NfcErrorCode.NFC_VALIDATION_FAILED, "Echec signature NFC.");
		}
	}

	private static boolean constantTimeEquals(String a, String b) {
		if (a == null || b == null || a.length() != b.length()) {
			return false;
		}
		int result = 0;
		for (int i = 0; i < a.length(); i++) {
			result |= a.charAt(i) ^ b.charAt(i);
		}
		return result == 0;
	}
}
