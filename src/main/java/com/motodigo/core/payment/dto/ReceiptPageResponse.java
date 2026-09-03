package com.motodigo.core.payment.dto;

import java.util.List;

public record ReceiptPageResponse(
		List<ReceiptResponse> receipts,
		int page,
		int size,
		boolean hasMore) {
}
