package com.motodigo.core.booking.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.motodigo.core.booking.entity.BookingRecord;
import com.motodigo.core.intercity.entity.IntercityAnnouncementRecord;

public record PayableBooking(
		BookingRecord booking,
		IntercityAnnouncementRecord announcement,
		BigDecimal amount,
		UUID transporterUserId) {
}
