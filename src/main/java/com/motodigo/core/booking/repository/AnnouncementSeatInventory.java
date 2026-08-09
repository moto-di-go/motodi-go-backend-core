package com.motodigo.core.booking.repository;

import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.intercity.entity.IntercityAnnouncementRecord;

/**
 * Seat-inventory operations needed by booking (pessimistic lock + remaining seats).
 * Backed by {@code intercity_announcements}; kept as a narrow port so booking tests
 * do not depend on the full intercity repository surface.
 */
public interface AnnouncementSeatInventory {

	Optional<IntercityAnnouncementRecord> findById(UUID id);

	Optional<IntercityAnnouncementRecord> lockByIdForUpdate(UUID id);

	IntercityAnnouncementRecord decrementRemainingSeats(UUID id, int seats);

	IntercityAnnouncementRecord incrementRemainingSeats(UUID id, int seats);
}
