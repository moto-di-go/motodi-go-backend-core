package com.motodigo.core.profile.controller;

import java.util.UUID;

import com.motodigo.core.profile.dto.CreateProfileRequest;
import com.motodigo.core.profile.dto.UpdateProfileRequest;
import com.motodigo.core.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
class ProfileController {

	private final ProfileService profileService;

	ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@GetMapping
	ResponseEntity<?> listProfiles(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(this.profileService.listProfiles(userId(jwt)));
	}

	@PostMapping
	ResponseEntity<?> createProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProfileRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(this.profileService.createProfile(userId(jwt), request));
	}

	@GetMapping("/current")
	ResponseEntity<?> currentProfile(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(this.profileService.currentProfile(userId(jwt)));
	}

	@PatchMapping("/{profileId}")
	ResponseEntity<?> updateProfile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID profileId,
			@Valid @RequestBody UpdateProfileRequest request) {
		return ResponseEntity.ok(this.profileService.updateProfile(userId(jwt), profileId, request));
	}

	@PatchMapping("/active/{profileId}")
	ResponseEntity<?> activateProfile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID profileId) {
		return ResponseEntity.ok(this.profileService.activateProfile(userId(jwt), profileId));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
