package com.motodigo.core.notification.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motodigo.core.notification.entity.NotificationRecord;

public record NotificationResponse(
		UUID id,
		String type,
		String title,
		String body,
		String status,
		Instant createdAt,
		Instant readAt,
		UUID bookingId,
		UUID paymentTransactionId,
		UUID tripId,
		Map<String, Object> metadata,
		boolean unread) {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	public static NotificationResponse from(NotificationRecord record) {
		return new NotificationResponse(
				record.id(),
				record.type(),
				record.title(),
				record.body(),
				record.status(),
				record.createdAt(),
				record.readAt(),
				record.bookingId(),
				record.paymentTransactionId(),
				record.tripId(),
				parseMetadata(record.metadataJson()),
				record.unread());
	}

	private static Map<String, Object> parseMetadata(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return MAPPER.readValue(json, MAP_TYPE);
		}
		catch (Exception ex) {
			return Map.of();
		}
	}
}
