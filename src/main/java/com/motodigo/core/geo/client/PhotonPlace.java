package com.motodigo.core.geo.client;

public record PhotonPlace(
		long osmId,
		String name,
		String subtitle,
		String kind,
		double lat,
		double lon) {
}
