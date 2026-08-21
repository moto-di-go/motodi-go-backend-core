package com.motodigo.core.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "motodigo.push")
public record PushProperties(
		boolean enabled,
		String projectId,
		String credentialsPath) {

	public PushProperties {
		if (projectId == null) {
			projectId = "";
		}
		if (credentialsPath == null) {
			credentialsPath = "";
		}
	}
}
