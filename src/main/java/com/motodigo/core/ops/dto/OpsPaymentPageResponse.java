package com.motodigo.core.ops.dto;

import java.util.List;

public record OpsPaymentPageResponse(
		List<OpsPaymentSummaryResponse> payments,
		int page,
		int size,
		boolean hasMore,
		String reconciliationEndpoint) {
}
