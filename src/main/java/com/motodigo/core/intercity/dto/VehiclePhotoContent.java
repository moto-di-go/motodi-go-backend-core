package com.motodigo.core.intercity.dto;

public record VehiclePhotoContent(
		byte[] bytes,
		String mimeType,
		String fileName) {
}
