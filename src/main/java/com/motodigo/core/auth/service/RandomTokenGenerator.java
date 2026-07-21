package com.motodigo.core.auth.service;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class RandomTokenGenerator {

	private final SecureRandom secureRandom = new SecureRandom();

	public String generateUrlSafeToken() {
		byte[] bytes = new byte[32];
		this.secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
