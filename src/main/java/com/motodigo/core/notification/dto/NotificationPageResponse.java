package com.motodigo.core.notification.dto;

import java.util.List;

public record NotificationPageResponse(
		List<NotificationResponse> items,
		int page,
		int size,
		boolean hasMore,
		long unreadCount) {
}
