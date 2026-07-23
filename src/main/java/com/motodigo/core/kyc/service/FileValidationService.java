package com.motodigo.core.kyc.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.motodigo.core.kyc.config.KycProperties;
import com.motodigo.core.kyc.exception.KycErrorCode;
import com.motodigo.core.kyc.exception.KycException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileValidationService {

	private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
	private static final Map<String, byte[]> SIGNATURES = Map.of(
			"image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
			"image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
			"application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D});

	private final long maxFileSizeBytes;

	public FileValidationService(KycProperties properties) {
		this.maxFileSizeBytes = properties.maxFileSizeBytes();
	}

	public ValidatedFile validate(MultipartFile file) {
		if (file == null || file.isEmpty() || file.getSize() <= 0) {
			throw new KycException(KycErrorCode.DOCUMENT_INVALID);
		}
		if (file.getSize() > this.maxFileSizeBytes) {
			throw new KycException(KycErrorCode.DOCUMENT_TOO_LARGE);
		}
		String mimeType = normalizeMimeType(file.getContentType());
		if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
			throw new KycException(KycErrorCode.DOCUMENT_MIME_NOT_ALLOWED);
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException ex) {
			throw new KycException(KycErrorCode.DOCUMENT_INVALID, ex);
		}
		if (bytes.length == 0) {
			throw new KycException(KycErrorCode.DOCUMENT_INVALID);
		}
		if (!matchesSignature(bytes, SIGNATURES.get(mimeType))) {
			throw new KycException(KycErrorCode.DOCUMENT_SIGNATURE_INVALID);
		}
		return new ValidatedFile(mimeType, bytes);
	}

	private static String normalizeMimeType(String value) {
		if (value == null || value.isBlank()) {
			throw new KycException(KycErrorCode.DOCUMENT_MIME_NOT_ALLOWED);
		}
		return value.toLowerCase(Locale.ROOT).split(";")[0].trim();
	}

	private static boolean matchesSignature(byte[] bytes, byte[] signature) {
		if (bytes.length < signature.length) {
			return false;
		}
		for (int i = 0; i < signature.length; i++) {
			if (bytes[i] != signature[i]) {
				return false;
			}
		}
		return true;
	}

	public record ValidatedFile(String mimeType, byte[] bytes) {
	}
}
