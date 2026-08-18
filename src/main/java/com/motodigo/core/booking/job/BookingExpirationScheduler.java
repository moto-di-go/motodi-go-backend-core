package com.motodigo.core.booking.job;

import com.motodigo.core.booking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class BookingExpirationScheduler {

	private static final Logger log = LoggerFactory.getLogger(BookingExpirationScheduler.class);

	private final BookingService bookingService;

	public BookingExpirationScheduler(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@Scheduled(cron = "${motodigo.booking.expiration-cron:0 * * * * *}")
	public void expireUnpaidBookings() {
		int expired = this.bookingService.expireUnpaidBookings();
		if (expired > 0) {
			log.info("Expired {} booking(s) past confirmation/payment TTL", expired);
		}
	}
}
