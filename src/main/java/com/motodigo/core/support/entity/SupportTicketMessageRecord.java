package com.motodigo.core.support.entity;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketMessageRecord(
		UUID id,
		UUID supportTicketId,
		UUID authorUserId,
		UUID authorAdminProfileId,
		String body,
		Instant createdAt) {
}
