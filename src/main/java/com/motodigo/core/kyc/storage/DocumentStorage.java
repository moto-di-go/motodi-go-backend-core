package com.motodigo.core.kyc.storage;

public interface DocumentStorage {

	String put(String key, byte[] content, String mimeType);

	byte[] get(String encryptedStorageKey);
}
