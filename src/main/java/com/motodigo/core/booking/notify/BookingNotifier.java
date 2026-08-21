package com.motodigo.core.booking.notify;

import java.util.UUID;

import com.motodigo.core.booking.entity.BookingRecord;

/**
 * Port for booking lifecycle notifications.
 * Production implementation: {@link NotificationDispatchBookingNotifier} (CORE-NOTIF-001).
 */
public interface BookingNotifier {

	enum NotificationType {
		SUBSCRIPTION,
		CONFIRMATION,
		CANCELLATION,
		EXPIRATION,
		PAYMENT
	}

	/**
	 * @param recipientUserIdHint currently a profile id hint resolved to user_id by the implementation
	 */
	void notifyBooking(BookingRecord booking, UUID recipientUserIdHint, NotificationType type);
}
