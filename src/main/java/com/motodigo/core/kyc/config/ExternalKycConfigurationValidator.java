package com.motodigo.core.kyc.config;

import java.util.Arrays;
import java.util.Set;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
class ExternalKycConfigurationValidator implements SmartInitializingSingleton {

	private static final Set<String> LOCAL_PROFILES = Set.of("local", "test");
	private static final String DEFAULT_STORAGE_ACCESS_KEY = "minioadmin";
	private static final String DEFAULT_STORAGE_SECRET_KEY = "minioadmin";
	private static final String DEFAULT_CHECKSUM_PEPPER = "change-me-kyc-checksum-pepper";
	private static final String DEFAULT_LOCAL_KEY_ID = "local";

	private final Environment environment;
	private final KycProperties properties;

	ExternalKycConfigurationValidator(Environment environment, KycProperties properties) {
		this.environment = environment;
		this.properties = properties;
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (isLocalOrTest()) {
			return;
		}
		requireNonDefault("KYC_STORAGE_ACCESS_KEY", this.properties.storage().accessKey(), DEFAULT_STORAGE_ACCESS_KEY);
		requireNonDefault("KYC_STORAGE_SECRET_KEY", this.properties.storage().secretKey(), DEFAULT_STORAGE_SECRET_KEY);
		requireNonDefault("KYC_CHECKSUM_PEPPER", this.properties.checksumPepper(), DEFAULT_CHECKSUM_PEPPER);
		if (DEFAULT_LOCAL_KEY_ID.equals(this.properties.encryption().activeKeyId())) {
			throw new IllegalStateException("KYC_ENCRYPTION_ACTIVE_KEY_ID must not use the local placeholder outside local/test profiles");
		}
		if (!this.properties.encryption().keys().containsKey(this.properties.encryption().activeKeyId())) {
			throw new IllegalStateException("KYC_ENCRYPTION_KEYS_<ID> must contain the active KYC encryption key");
		}
	}

	private boolean isLocalOrTest() {
		return Arrays.stream(this.environment.getActiveProfiles()).anyMatch(LOCAL_PROFILES::contains);
	}

	private static void requireNonDefault(String name, String value, String defaultValue) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " is required outside local/test profiles");
		}
		if (defaultValue.equals(value)) {
			throw new IllegalStateException(name + " must not use the local placeholder outside local/test profiles");
		}
	}
}
