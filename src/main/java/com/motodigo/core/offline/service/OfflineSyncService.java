package com.motodigo.core.offline.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motodigo.core.nfc.service.NfcPaymentService;
import com.motodigo.core.nfc.service.NfcPaymentService.RegularizeResult;
import com.motodigo.core.offline.config.OfflineProperties;
import com.motodigo.core.offline.dto.OfflineMapAreaResponse;
import com.motodigo.core.offline.dto.OfflineOperationInput;
import com.motodigo.core.offline.dto.OfflineOperationPageResponse;
import com.motodigo.core.offline.dto.OfflineOperationResponse;
import com.motodigo.core.offline.dto.OfflineSyncItemResult;
import com.motodigo.core.offline.dto.OfflineSyncRequest;
import com.motodigo.core.offline.dto.OfflineSyncResponse;
import com.motodigo.core.offline.entity.OfflineOperationRecord;
import com.motodigo.core.offline.entity.SyncBatchRecord;
import com.motodigo.core.offline.exception.OfflineErrorCode;
import com.motodigo.core.offline.exception.OfflineException;
import com.motodigo.core.offline.repository.OfflineSyncRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class OfflineSyncService {

	private static final Set<String> OPERATION_TYPES = Set.of(
			"BOOKING_CREATE", "BOOKING_CANCEL", "NFC_PAYMENT", "PROFILE_UPDATE", "SUPPORT_TICKET");

	private final OfflineSyncRepository offlineSyncRepository;
	private final NfcPaymentService nfcPaymentService;
	private final OfflineProperties offlineProperties;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	@Autowired
	public OfflineSyncService(OfflineSyncRepository offlineSyncRepository, NfcPaymentService nfcPaymentService,
			OfflineProperties offlineProperties, ObjectMapper objectMapper) {
		this(offlineSyncRepository, nfcPaymentService, offlineProperties, objectMapper, Clock.systemUTC());
	}

	OfflineSyncService(OfflineSyncRepository offlineSyncRepository, NfcPaymentService nfcPaymentService,
			OfflineProperties offlineProperties, ObjectMapper objectMapper, Clock clock) {
		this.offlineSyncRepository = offlineSyncRepository;
		this.nfcPaymentService = nfcPaymentService;
		this.offlineProperties = offlineProperties;
		this.objectMapper = objectMapper;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	@Transactional
	public OfflineSyncResponse sync(UUID userId, OfflineSyncRequest request) {
		if (request == null || request.operations() == null || request.operations().isEmpty()) {
			throw new OfflineException(OfflineErrorCode.OFFLINE_VALIDATION_FAILED);
		}

		Instant now = Instant.now(this.clock);
		SyncBatchRecord batch = this.offlineSyncRepository.insertBatch(userId);
		List<OfflineSyncItemResult> results = new ArrayList<>();
		int synced = 0;
		int conflicts = 0;
		int failed = 0;

		for (OfflineOperationInput input : request.operations()) {
			OfflineSyncItemResult result = processOne(userId, batch.id(), input, now);
			results.add(result);
			switch (result.outcome()) {
				case "SYNCED", "IGNORED" -> synced++;
				case "CONFLICT" -> conflicts++;
				default -> failed++;
			}
		}

		String batchStatus;
		if (failed == results.size()) {
			batchStatus = "FAILED";
		}
		else if (conflicts > 0 || failed > 0) {
			batchStatus = "PARTIAL_SUCCESS";
		}
		else {
			batchStatus = "COMPLETED";
		}
		this.offlineSyncRepository.completeBatch(batch.id(), batchStatus, Instant.now(this.clock));
		return new OfflineSyncResponse(batch.id(), batchStatus, results);
	}

	@Transactional(readOnly = true)
	public OfflineOperationPageResponse listOperations(UUID userId, String status, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.max(1, Math.min(size, 100));
		List<OfflineOperationRecord> rows = this.offlineSyncRepository.listForUser(userId, status, safeSize + 1,
				safePage * safeSize);
		boolean hasMore = rows.size() > safeSize;
		List<OfflineOperationResponse> operations = rows.stream().limit(safeSize)
				.map(row -> OfflineOperationResponse.from(row, parsePayload(row.payloadJson())))
				.toList();
		return new OfflineOperationPageResponse(operations, safePage, safeSize, hasMore);
	}

	@Transactional(readOnly = true)
	public List<OfflineMapAreaResponse> mapAreas() {
		Instant expiresAt = Instant.now(this.clock).plus(this.offlineProperties.mapPackTtlDays(), ChronoUnit.DAYS);
		String base = this.offlineProperties.mapBaseUrl();
		return List.of(
				new OfflineMapAreaResponse("douala", "Douala",
						"POLYGON((9.60 3.95, 9.90 3.95, 9.90 4.15, 9.60 4.15, 9.60 3.95))",
						45_000_000L, base + "/douala.mbtiles", expiresAt, false),
				new OfflineMapAreaResponse("yaounde", "Yaounde",
						"POLYGON((11.40 3.75, 11.60 3.75, 11.60 3.95, 11.40 3.95, 11.40 3.75))",
						38_000_000L, base + "/yaounde.mbtiles", expiresAt, false),
				new OfflineMapAreaResponse("axe-douala-yaounde", "Axe Douala-Yaounde",
						"POLYGON((9.60 3.75, 11.60 3.75, 11.60 4.15, 9.60 4.15, 9.60 3.75))",
						72_000_000L, base + "/axe-douala-yaounde.mbtiles", expiresAt, false));
	}

	private OfflineSyncItemResult processOne(UUID userId, UUID batchId, OfflineOperationInput input, Instant now) {
		if (input == null || input.type() == null || input.idempotencyKey() == null || input.payload() == null) {
			return new OfflineSyncItemResult(input == null ? null : input.idempotencyKey(), "FAILED", null, null,
					"Operation invalide.");
		}
		String type = input.type().trim().toUpperCase(Locale.ROOT);
		if (!OPERATION_TYPES.contains(type)) {
			return new OfflineSyncItemResult(input.idempotencyKey(), "FAILED", null, null, "Type inconnu.");
		}

		Optional<OfflineOperationRecord> existing = this.offlineSyncRepository.findByUserAndIdempotency(userId,
				input.idempotencyKey());
		if (existing.isPresent()) {
			OfflineOperationRecord op = existing.get();
			if ("SYNCED".equals(op.status())) {
				return new OfflineSyncItemResult(input.idempotencyKey(), "IGNORED", op.id(), null,
						"Deja synchronise.");
			}
			if ("CONFLICT".equals(op.status())) {
				return new OfflineSyncItemResult(input.idempotencyKey(), "CONFLICT", op.id(), op.conflictCode(),
						op.conflictMessage());
			}
		}

		String payloadJson = toJson(input.payload());
		Instant queuedAt = input.queuedAt() == null ? now : input.queuedAt();
		OfflineOperationRecord created = existing.orElseGet(() -> this.offlineSyncRepository.insertOperation(userId,
				batchId, type, payloadJson, input.idempotencyKey(), queuedAt));

		if ("NFC_PAYMENT".equals(type)) {
			return processNfc(created, input.payload(), input.idempotencyKey());
		}

		OfflineOperationRecord ignored = this.offlineSyncRepository.markIgnored(created.id());
		return new OfflineSyncItemResult(input.idempotencyKey(), "IGNORED", ignored.id(), null,
				"Type non traite dans ce lot.");
	}

	private OfflineSyncItemResult processNfc(OfflineOperationRecord operation, Map<String, Object> payload,
			String idempotencyKey) {
		String signedToken = stringValue(payload.get("signedToken"));
		if (signedToken == null) {
			signedToken = stringValue(payload.get("token"));
		}
		BigDecimal amount = decimalValue(payload.get("amount"));
		UUID tripId = uuidValue(payload.get("tripId"));
		if (signedToken == null || signedToken.isBlank()) {
			OfflineOperationRecord failed = this.offlineSyncRepository.markFailed(operation.id(),
					"signedToken manquant.");
			return new OfflineSyncItemResult(idempotencyKey, "FAILED", failed.id(), null, "signedToken manquant.");
		}

		RegularizeResult result = this.nfcPaymentService.regularize(signedToken, amount, tripId, idempotencyKey);
		if (result.success()) {
			OfflineOperationRecord synced = this.offlineSyncRepository.markSynced(operation.id(), result.tokenId(),
					null, Instant.now(this.clock));
			return new OfflineSyncItemResult(idempotencyKey, "SYNCED", synced.id(), null, null);
		}

		OfflineOperationRecord conflict = this.offlineSyncRepository.markConflict(operation.id(),
				result.conflictCode(), result.message());
		return new OfflineSyncItemResult(idempotencyKey, "CONFLICT", conflict.id(), result.conflictCode(),
				result.message());
	}

	private Map<String, Object> parsePayload(String payloadJson) {
		if (payloadJson == null || payloadJson.isBlank()) {
			return Map.of();
		}
		try {
			return this.objectMapper.readValue(payloadJson, new TypeReference<>() {
			});
		}
		catch (Exception ex) {
			Map<String, Object> fallback = new LinkedHashMap<>();
			fallback.put("raw", payloadJson);
			return fallback;
		}
	}

	private String toJson(Map<String, Object> payload) {
		try {
			return this.objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
		}
		catch (JsonProcessingException ex) {
			throw new OfflineException(OfflineErrorCode.OFFLINE_VALIDATION_FAILED, "Payload JSON invalide.");
		}
	}

	private static String stringValue(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static BigDecimal decimalValue(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof BigDecimal bd) {
			return bd;
		}
		if (value instanceof Number number) {
			return BigDecimal.valueOf(number.doubleValue());
		}
		try {
			return new BigDecimal(String.valueOf(value));
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static UUID uuidValue(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof UUID uuid) {
			return uuid;
		}
		try {
			return UUID.fromString(String.valueOf(value));
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
