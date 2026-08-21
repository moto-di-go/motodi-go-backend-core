package com.motodigo.core.booking.notify;

import java.util.UUID;

import com.motodigo.core.booking.entity.BookingRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Fallback when no datasource-backed dispatcher is available (e.g. unit tests without DB).
 */
@Component
@ConditionalOnMissingBean(BookingNotifier.class)
public class LoggingBookingNotifier implements BookingNotifier {

	private static final Logger log = LoggerFactory.getLogger(LoggingBookingNotifier.class);

	@Override
	public void notifyBooking(BookingRecord booking, UUID recipientUserIdHint, NotificationType type) {
		log.info("Booking notification type={} bookingId={} status={} recipientHint={}",
				type, booking.id(), booking.status(), recipientUserIdHint);
	}
}
