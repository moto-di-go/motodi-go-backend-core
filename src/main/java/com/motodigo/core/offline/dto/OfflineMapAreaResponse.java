package com.motodigo.core.offline.dto;

import java.time.Instant;

public record OfflineMapAreaResponse(
		String id,
		String areaName,
		String boundingBoxWkt,
		long sizeBytes,
		String downloadUrl,
		Instant expiresAt,
		boolean downloaded) {
}
