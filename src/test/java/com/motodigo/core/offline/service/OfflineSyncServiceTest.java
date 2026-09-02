package com.motodigo.core.offline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motodigo.core.nfc.service.NfcPaymentService;
import com.motodigo.core.nfc.service.NfcPaymentService.RegularizeResult;
import com.motodigo.core.offline.config.OfflineProperties;
import com.motodigo.core.offline.dto.OfflineOperationInput;
import com.motodigo.core.offline.dto.OfflineSyncRequest;
import com.motodigo.core.offline.entity.OfflineOperationRecord;
import com.motodigo.core.offline.entity.SyncBatchRecord;
import com.motodigo.core.offline.repository.OfflineSyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineSyncServiceTest {

	@Mock
	private OfflineSyncRepository offlineSyncRepository;
	@Mock
	private NfcPaymentService nfcPaymentService;

	private OfflineSyncService service;
	private Instant now;
	private UUID userId;
	private UUID batchId;
	private UUID operationId;

	@BeforeEach
	void setUp() {
		this.now = Instant.parse("2026-09-02T18:00:00Z");
		this.userId = UUID.randomUUID();
		this.batchId = UUID.randomUUID();
		this.operationId = UUID.randomUUID();
		this.service = new OfflineSyncService(this.offlineSyncRepository, this.nfcPaymentService,
				new OfflineProperties("http://maps.example/packs", 30), new ObjectMapper(),
				Clock.fixed(this.now, ZoneOffset.UTC));
	}

	@Test
	void syncNfcPaymentCallsRegularizeAndMarksSynced() {
		when(this.offlineSyncRepository.insertBatch(this.userId))
				.thenReturn(new SyncBatchRecord(this.batchId, this.userId, this.now, null, "STARTED"));
		when(this.offlineSyncRepository.findByUserAndIdempotency(this.userId, "nfc-1"))
				.thenReturn(Optional.empty());
		OfflineOperationRecord created = operation("SYNCING", null, null);
		when(this.offlineSyncRepository.insertOperation(eq(this.userId), eq(this.batchId), eq("NFC_PAYMENT"), any(),
				eq("nfc-1"), any())).thenReturn(created);
		UUID tokenId = UUID.randomUUID();
		when(this.nfcPaymentService.regularize(eq("signed.token"), eq(new BigDecimal("1500")), isNull(), eq("nfc-1")))
				.thenReturn(RegularizeResult.synced(tokenId, UUID.randomUUID()));
		when(this.offlineSyncRepository.markSynced(eq(this.operationId), eq(tokenId), isNull(), eq(this.now)))
				.thenReturn(operation("SYNCED", null, null));
		when(this.offlineSyncRepository.completeBatch(eq(this.batchId), eq("COMPLETED"), any()))
				.thenReturn(new SyncBatchRecord(this.batchId, this.userId, this.now, this.now, "COMPLETED"));

		var response = this.service.sync(this.userId, new OfflineSyncRequest(List.of(
				new OfflineOperationInput("NFC_PAYMENT", "nfc-1",
						Map.of("signedToken", "signed.token", "amount", "1500"), this.now))));

		assertThat(response.batchId()).isEqualTo(this.batchId);
		assertThat(response.status()).isEqualTo("COMPLETED");
		assertThat(response.results()).hasSize(1);
		assertThat(response.results().get(0).outcome()).isEqualTo("SYNCED");
		verify(this.nfcPaymentService).regularize("signed.token", new BigDecimal("1500"), null, "nfc-1");
	}

	@Test
	void syncNfcConflictPropagatesStableCode() {
		when(this.offlineSyncRepository.insertBatch(this.userId))
				.thenReturn(new SyncBatchRecord(this.batchId, this.userId, this.now, null, "STARTED"));
		when(this.offlineSyncRepository.findByUserAndIdempotency(this.userId, "nfc-2"))
				.thenReturn(Optional.empty());
		when(this.offlineSyncRepository.insertOperation(any(), any(), any(), any(), any(), any()))
				.thenReturn(operation("SYNCING", null, null));
		when(this.nfcPaymentService.regularize(any(), any(), any(), any()))
				.thenReturn(RegularizeResult.conflict("TOKEN_EXPIRED", "expire"));
		when(this.offlineSyncRepository.markConflict(this.operationId, "TOKEN_EXPIRED", "expire"))
				.thenReturn(operation("CONFLICT", "TOKEN_EXPIRED", "expire"));
		when(this.offlineSyncRepository.completeBatch(eq(this.batchId), eq("PARTIAL_SUCCESS"), any()))
				.thenReturn(new SyncBatchRecord(this.batchId, this.userId, this.now, this.now, "PARTIAL_SUCCESS"));

		var response = this.service.sync(this.userId, new OfflineSyncRequest(List.of(
				new OfflineOperationInput("NFC_PAYMENT", "nfc-2", Map.of("signedToken", "x"), null))));

		assertThat(response.results().get(0).outcome()).isEqualTo("CONFLICT");
		assertThat(response.results().get(0).conflictCode()).isEqualTo("TOKEN_EXPIRED");
		assertThat(response.status()).isEqualTo("PARTIAL_SUCCESS");
	}

	@Test
	void mapAreasReturnsStaticCatalogue() {
		var areas = this.service.mapAreas();
		assertThat(areas).hasSize(3);
		assertThat(areas).extracting(a -> a.id()).containsExactly("douala", "yaounde", "axe-douala-yaounde");
		assertThat(areas.get(0).downloadUrl()).startsWith("http://maps.example/packs/");
	}

	private OfflineOperationRecord operation(String status, String conflictCode, String conflictMessage) {
		return new OfflineOperationRecord(this.operationId, this.userId, this.batchId, "NFC_PAYMENT", "{}", "nfc-1",
				status, 0, this.now, "SYNCED".equals(status) ? this.now : null, null, null, conflictCode,
				conflictMessage);
	}
}
