package com.motodigo.core.booking.notify;

import java.util.UUID;

import com.motodigo.core.booking.entity.BookingRecord;

/**
 * Port for booking lifecycle notifications. CORE-NOTIF-001 will provide FCM/APNs;
 * the default implementation only logs.
 */
public interface BookingNotifier {

	enum NotificationType {
		SUBSCRIPTION,
		CONFIRMATION,
		CANCELLATION,
		EXPIRATION
	}

	void notifyBooking(BookingRecord booking, UUID recipientUserIdHint, NotificationType type);
}
