package com.motodigo.core.intercity.support;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Folding casse + accents pour la recherche de villes/pt. ramassage
 * (ex. {@code Yaounde} == {@code Yaoundé}).
 */
public final class AccentFold {

	private AccentFold() {
	}

	public static String fold(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim().replaceAll("\\s+", " ");
		if (trimmed.isEmpty()) {
			return null;
		}
		String nfd = Normalizer.normalize(trimmed, Normalizer.Form.NFD);
		return nfd.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
	}
}
