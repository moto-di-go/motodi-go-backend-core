package com.motodigo.core.kyc.storage;

public interface DocumentStorage {

	byte[] get(String encryptedStorageKey);
}
