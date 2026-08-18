package com.motodigo.core.geo.dto;

public record RouteMatchResponse(String polyline, double distanceKm, int durationMinutes, String engine) {
}
