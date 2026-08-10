package com.motodigo.core.intercity.controller;

import java.util.UUID;

import com.motodigo.core.intercity.dto.VehiclePhotoContent;
import com.motodigo.core.intercity.service.VehicleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class VehicleController {

	private final VehicleService vehicleService;

	VehicleController(VehicleService vehicleService) {
		this.vehicleService = vehicleService;
	}

	@GetMapping("/mine")
	ResponseEntity<?> listMine(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(this.vehicleService.listMine(userId(jwt), activeProfileId(jwt)));
	}

	@GetMapping("/{vehicleId}/photo")
	ResponseEntity<byte[]> getPhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId) {
		VehiclePhotoContent photo = this.vehicleService.getPhoto(userId(jwt), activeProfileId(jwt), vehicleId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFileName(photo.fileName()) + "\"")
				.contentType(MediaType.parseMediaType(photo.mimeType()))
				.body(photo.bytes());
	}

	private static String safeFileName(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return "vehicle-photo.jpg";
		}
		return fileName.replace("\"", "");
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	private static UUID activeProfileId(Jwt jwt) {
		String claim = jwt.getClaimAsString("activeProfileId");
		if (claim == null || claim.isBlank()) {
			return null;
		}
		return UUID.fromString(claim);
	}
}
