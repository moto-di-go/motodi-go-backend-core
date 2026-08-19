package com.motodigo.core.geo.controller;

import java.util.List;

import com.motodigo.core.geo.dto.PhotonIndexStatusResponse;
import com.motodigo.core.geo.exception.GeoErrorCode;
import com.motodigo.core.geo.exception.GeoException;
import com.motodigo.core.geo.service.PhotonIndexService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/gis/photon")
class AdminPhotonController {

	private final PhotonIndexService photonIndexService;

	AdminPhotonController(PhotonIndexService photonIndexService) {
		this.photonIndexService = photonIndexService;
	}

	@GetMapping
	ResponseEntity<PhotonIndexStatusResponse> status(@AuthenticationPrincipal Jwt jwt) {
		requirePermission(jwt, "GIS_READ");
		return ResponseEntity.ok(this.photonIndexService.status());
	}

	@PostMapping("/update")
	ResponseEntity<PhotonIndexStatusResponse> update(@AuthenticationPrincipal Jwt jwt) {
		requirePermission(jwt, "GIS_WRITE");
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(this.photonIndexService.startUpdate());
	}

	private static void requirePermission(Jwt jwt, String permission) {
		List<String> permissions = jwt.getClaimAsStringList("permissions");
		if (permissions == null || !permissions.contains(permission)) {
			throw new GeoException(GeoErrorCode.ADMIN_REQUIRED);
		}
	}
}
