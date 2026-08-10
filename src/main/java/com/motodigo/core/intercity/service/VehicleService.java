package com.motodigo.core.intercity.service;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.intercity.dto.VehiclePhotoContent;
import com.motodigo.core.intercity.dto.VehicleResponse;
import com.motodigo.core.intercity.entity.VehicleRecord;
import com.motodigo.core.intercity.exception.IntercityErrorCode;
import com.motodigo.core.intercity.exception.IntercityException;
import com.motodigo.core.intercity.repository.IntercityAnnouncementRepository;
import com.motodigo.core.intercity.repository.VehicleLookupRepository;
import com.motodigo.core.kyc.entity.OfficialDocumentRecord;
import com.motodigo.core.kyc.storage.DocumentStorage;
import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import com.motodigo.core.profile.repository.ProfileRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class VehicleService {

	private final VehicleLookupRepository vehicleLookupRepository;
	private final IntercityAnnouncementRepository announcementRepository;
	private final ProfileRepository profileRepository;
	private final DocumentStorage documentStorage;

	public VehicleService(VehicleLookupRepository vehicleLookupRepository,
			IntercityAnnouncementRepository announcementRepository, ProfileRepository profileRepository,
			DocumentStorage documentStorage) {
		this.vehicleLookupRepository = vehicleLookupRepository;
		this.announcementRepository = announcementRepository;
		this.profileRepository = profileRepository;
		this.documentStorage = documentStorage;
	}

	@Transactional(readOnly = true)
	public List<VehicleResponse> listMine(UUID userId, UUID activeProfileId) {
		ProfileRecord publisher = requirePublisherProfile(userId, activeProfileId);
		return this.vehicleLookupRepository.findValidatedActiveByOwner(publisher.id()).stream()
				.map(VehicleResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public VehiclePhotoContent getPhoto(UUID userId, UUID activeProfileId, UUID vehicleId) {
		VehicleRecord vehicle = this.vehicleLookupRepository.findById(vehicleId)
				.orElseThrow(() -> new IntercityException(IntercityErrorCode.VEHICLE_INVALID));

		boolean owner = activeProfileId != null
				&& activeProfileId.equals(vehicle.ownerProfileId())
				&& this.profileRepository.findOwnedById(userId, activeProfileId).isPresent();
		boolean publiclyVisible = this.announcementRepository.existsPublishedOrFullWithVehicle(vehicleId);
		if (!owner && !publiclyVisible) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_NOT_FOUND);
		}

		OfficialDocumentRecord photo = this.vehicleLookupRepository.findAcceptedVehiclePhoto(vehicleId)
				.orElseThrow(() -> new IntercityException(IntercityErrorCode.VEHICLE_INVALID,
						"Photo du véhicule introuvable."));
		byte[] bytes = this.documentStorage.get(photo.encryptedStorageKey());
		String mime = photo.mimeType() == null || photo.mimeType().isBlank() ? "image/jpeg" : photo.mimeType();
		return new VehiclePhotoContent(bytes, mime, photo.fileName());
	}

	private ProfileRecord requirePublisherProfile(UUID userId, UUID activeProfileId) {
		if (activeProfileId == null) {
			throw new IntercityException(IntercityErrorCode.ACTIVE_PROFILE_REQUIRED);
		}
		ProfileRecord profile = this.profileRepository.findOwnedById(userId, activeProfileId)
				.orElseThrow(() -> new IntercityException(IntercityErrorCode.ACTIVE_PROFILE_REQUIRED));
		if (!profile.active()) {
			throw new IntercityException(IntercityErrorCode.ACTIVE_PROFILE_REQUIRED);
		}
		if (profile.type() != ProfileType.TRANSPORTER && profile.type() != ProfileType.AGENCY) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_FORBIDDEN);
		}
		if (profile.validationStatus() != ValidationStatus.VALIDATED) {
			throw new IntercityException(IntercityErrorCode.ANNOUNCEMENT_FORBIDDEN,
					"Seuls les profils validés peuvent gérer des véhicules.");
		}
		return profile;
	}
}
