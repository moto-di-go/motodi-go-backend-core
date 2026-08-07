package com.motodigo.core.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.wallet.entity.LedgerEntryRecord;

public record LedgerEntryResponse(
		UUID id,
		String type,
		BigDecimal amount,
		String currency,
		String reference,
		Instant bookedAt) {

	public static LedgerEntryResponse from(LedgerEntryRecord entry) {
		return new LedgerEntryResponse(
				entry.id(),
				entry.type().name(),
				entry.amount(),
				entry.currency(),
				entry.reference(),
				entry.bookedAt());
	}
}
