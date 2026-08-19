package com.motodigo.core.geo.dto;

public record PhotonIndexStatusResponse(
		boolean enabled,
		boolean ready,
		boolean updating,
		boolean manualUpdateAvailable,
		String message) {
}
