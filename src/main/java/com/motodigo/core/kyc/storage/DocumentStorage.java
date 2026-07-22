package com.motodigo.core.kyc.storage;

public interface DocumentStorage {

	String put(String objectKey, byte[] encryptedContent, String mimeType);

	byte[] get(String encryptedStorageKey);
}
