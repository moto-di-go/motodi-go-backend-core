package com.motodigo.core.support.dto;

import java.time.Instant;
import java.util.UUID;

import com.motodigo.core.support.entity.SupportTicketMessageRecord;

public record SupportTicketMessageResponse(
		UUID id,
		String body,
		UUID authorUserId,
		UUID authorAdminProfileId,
		Instant createdAt) {

	public static SupportTicketMessageResponse from(SupportTicketMessageRecord message) {
		return new SupportTicketMessageResponse(
				message.id(),
				message.body(),
				message.authorUserId(),
				message.authorAdminProfileId(),
				message.createdAt());
	}
}
