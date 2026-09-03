package com.motodigo.core.offline.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record OfflineSyncRequest(
		@NotEmpty List<@Valid OfflineOperationInput> operations) {
}
