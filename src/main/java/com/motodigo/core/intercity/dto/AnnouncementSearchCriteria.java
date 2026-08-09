package com.motodigo.core.intercity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.motodigo.core.intercity.entity.AnnouncementSort;

public record AnnouncementSearchCriteria(
		String departureCity,
		String destinationCity,
		LocalDate date,
		BigDecimal maxPrice,
		LocalTime timeFrom,
		LocalTime timeTo,
		String quarter,
		boolean onlyAvailable,
		AnnouncementSort sort,
		Double nearLat,
		Double nearLon,
		int page,
		int size) {
}
