package com.motodigo.core.auth.service;

import com.motodigo.core.auth.exception.AuthErrorCode;
import com.motodigo.core.auth.exception.AuthException;
import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {

	private static final String CAMEROON_PREFIX = "+237";

	public String normalize(String rawPhoneNumber) {
		if (rawPhoneNumber == null) {
			throw new AuthException(AuthErrorCode.INVALID_PHONE_NUMBER);
		}
		String digits = rawPhoneNumber.trim().replace(" ", "").replace("-", "");
		if (digits.startsWith("+")) {
			digits = "+" + digits.substring(1).replaceAll("\\D", "");
		}
		else {
			digits = digits.replaceAll("\\D", "");
		}

		if (digits.startsWith(CAMEROON_PREFIX)) {
			return validateNational(digits.substring(CAMEROON_PREFIX.length()));
		}
		if (digits.startsWith("237")) {
			return validateNational(digits.substring(3));
		}
		if (digits.length() == 9) {
			return validateNational(digits);
		}
		throw new AuthException(AuthErrorCode.INVALID_PHONE_NUMBER);
	}

	private String validateNational(String nationalNumber) {
		if (nationalNumber.matches("6\\d{8}")) {
			return CAMEROON_PREFIX + nationalNumber;
		}
		throw new AuthException(AuthErrorCode.INVALID_PHONE_NUMBER);
	}
}
