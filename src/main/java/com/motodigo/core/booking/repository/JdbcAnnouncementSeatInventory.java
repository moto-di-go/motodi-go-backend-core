package com.motodigo.core.booking.repository;

import java.util.Optional;
import java.util.UUID;

import com.motodigo.core.intercity.entity.IntercityAnnouncementRecord;
import com.motodigo.core.intercity.repository.IntercityAnnouncementRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class JdbcAnnouncementSeatInventory implements AnnouncementSeatInventory {

	private final IntercityAnnouncementRepository announcementRepository;

	public JdbcAnnouncementSeatInventory(IntercityAnnouncementRepository announcementRepository) {
		this.announcementRepository = announcementRepository;
	}

	@Override
	public Optional<IntercityAnnouncementRecord> findById(UUID id) {
		return this.announcementRepository.findById(id);
	}

	@Override
	public Optional<IntercityAnnouncementRecord> lockByIdForUpdate(UUID id) {
		return this.announcementRepository.lockByIdForUpdate(id);
	}

	@Override
	public IntercityAnnouncementRecord decrementRemainingSeats(UUID id, int seats) {
		return this.announcementRepository.decrementRemainingSeats(id, seats);
	}

	@Override
	public IntercityAnnouncementRecord incrementRemainingSeats(UUID id, int seats) {
		return this.announcementRepository.incrementRemainingSeats(id, seats);
	}
}
