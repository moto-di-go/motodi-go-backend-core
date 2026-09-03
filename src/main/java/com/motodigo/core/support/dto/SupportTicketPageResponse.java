package com.motodigo.core.support.dto;

import java.util.List;

public record SupportTicketPageResponse(
		List<SupportTicketResponse> tickets,
		int page,
		int size,
		boolean hasMore) {
}
