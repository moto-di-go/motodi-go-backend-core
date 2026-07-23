package com.motodigo.core.kyc.service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.motodigo.core.kyc.config.KycProperties;
import com.motodigo.core.kyc.exception.KycErrorCode;
import com.motodigo.core.kyc.exception.KycException;
import org.springframework.stereotype.Service;

@Service
public class KycCryptoService {

	private static final byte[] MAGIC = "MDG-KYC1\n".getBytes(StandardCharsets.US_ASCII);
	private static final int IV_BYTES = 12;
	private static final int GCM_TAG_BITS = 128;

	private final String activeKeyId;
	private final Map<String, byte[]> keys;
	private final byte[] checksumPepper;
	private final SecureRandom secureRandom = new SecureRandom();

	public KycCryptoService(KycProperties properties) {
		this.activeKeyId = properties.encryption().activeKeyId();
		this.keys = decodeKeys(properties.encryption().keys());
		this.checksumPepper = properties.checksumPepper().getBytes(StandardCharsets.UTF_8);
		if (!this.keys.containsKey(this.activeKeyId)) {
			throw new KycException(KycErrorCode.KYC_CONFIGURATION_INVALID);
		}
	}

	public byte[] encrypt(byte[] plaintext) {
		try {
			byte[] iv = new byte[IV_BYTES];
			this.secureRandom.nextBytes(iv);
			byte[] keyIdBytes = this.activeKeyId.getBytes(StandardCharsets.UTF_8);
			if (keyIdBytes.length > 255) {
				throw new KycException(KycErrorCode.KYC_CONFIGURATION_INVALID);
			}
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(this.keys.get(this.activeKeyId), "AES"),
					new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext);
			ByteArrayOutputStream output = new ByteArrayOutputStream(MAGIC.length + 1 + keyIdBytes.length + iv.length
					+ ciphertext.length);
			output.writeBytes(MAGIC);
			output.write(keyIdBytes.length);
			output.writeBytes(keyIdBytes);
			output.writeBytes(iv);
			output.writeBytes(ciphertext);
			return output.toByteArray();
		}
		catch (GeneralSecurityException ex) {
			throw new KycException(KycErrorCode.KYC_CRYPTO_FAILED, ex);
		}
	}

	public byte[] decrypt(byte[] encryptedPayload) {
		try {
			ByteBuffer buffer = ByteBuffer.wrap(encryptedPayload);
			for (byte expected : MAGIC) {
				if (!buffer.hasRemaining() || buffer.get() != expected) {
					throw new KycException(KycErrorCode.KYC_CRYPTO_FAILED);
				}
			}
			int keyIdLength = Byte.toUnsignedInt(buffer.get());
			if (keyIdLength == 0 || buffer.remaining() <= keyIdLength + IV_BYTES) {
				throw new KycException(KycErrorCode.KYC_CRYPTO_FAILED);
			}
			byte[] keyIdBytes = new byte[keyIdLength];
			buffer.get(keyIdBytes);
			String keyId = new String(keyIdBytes, StandardCharsets.UTF_8);
			byte[] key = this.keys.get(keyId);
			if (key == null) {
				throw new KycException(KycErrorCode.KYC_CRYPTO_FAILED);
			}
			byte[] iv = new byte[IV_BYTES];
			buffer.get(iv);
			byte[] ciphertext = new byte[buffer.remaining()];
			buffer.get(ciphertext);

			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
			return cipher.doFinal(ciphertext);
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			throw new KycException(KycErrorCode.KYC_CRYPTO_FAILED, ex);
		}
	}

	public String checksum(byte[] plaintext) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(this.checksumPepper, "HmacSHA256"));
			return Base64.getEncoder().encodeToString(mac.doFinal(plaintext));
		}
		catch (GeneralSecurityException ex) {
			throw new KycException(KycErrorCode.KYC_CRYPTO_FAILED, ex);
		}
	}

	private static Map<String, byte[]> decodeKeys(Map<String, String> encodedKeys) {
		Map<String, byte[]> decoded = new HashMap<>();
		encodedKeys.forEach((keyId, encodedValue) -> {
			if (encodedValue == null || encodedValue.isBlank()) {
				return;
			}
			byte[] key;
			try {
				key = Base64.getDecoder().decode(encodedValue);
			}
			catch (IllegalArgumentException ex) {
				throw new KycException(KycErrorCode.KYC_CONFIGURATION_INVALID, ex);
			}
			if (key.length != 32) {
				throw new KycException(KycErrorCode.KYC_CONFIGURATION_INVALID);
			}
			decoded.put(keyId, key);
			decoded.putIfAbsent(keyId.toLowerCase(Locale.ROOT), key);
		});
		return Map.copyOf(decoded);
	}
}
