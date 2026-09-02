package com.motodigo.core.nfc.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NfcEligibilityResponse(
		boolean eligible,
		boolean walletActive,
		BigDecimal availableBalance,
		BigDecimal maxAmount,
		BigDecimal offlineQuotaRemaining,
		Instant offlineQuotaExpiresAt,
		List<String> reasons) {
}
