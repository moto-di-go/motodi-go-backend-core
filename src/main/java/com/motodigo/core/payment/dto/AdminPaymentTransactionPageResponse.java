package com.motodigo.core.payment.dto;

import java.util.List;

public record AdminPaymentTransactionPageResponse(
		List<DepositResponse> transactions,
		int page,
		int size,
		boolean hasMore) {
}
