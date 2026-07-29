package com.motodigo.core.wallet.dto;

import java.util.List;

public record LedgerPageResponse(
		List<LedgerEntryResponse> entries,
		int page,
		int size,
		boolean hasMore) {
}
