package com.motodigo.core.auth.service;

public record RequestMetadata(String ipAddress, String userAgent, String correlationId) {
}
