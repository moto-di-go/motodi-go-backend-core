package com.motodigo.core.kyc.storage;

import java.net.URI;

import com.motodigo.core.kyc.config.KycProperties;
import com.motodigo.core.kyc.exception.KycErrorCode;
import com.motodigo.core.kyc.exception.KycException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class S3DocumentStorage implements DocumentStorage {

	private final KycProperties properties;
	private final S3Client s3Client;

	public S3DocumentStorage(KycProperties properties) {
		this.properties = properties;
		this.s3Client = S3Client.builder()
				.endpointOverride(URI.create(properties.storage().endpoint()))
				.region(Region.of(properties.storage().region()))
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
						properties.storage().accessKey(), properties.storage().secretKey())))
				.forcePathStyle(properties.storage().pathStyle())
				.build();
	}

	@Override
	public String put(String key, byte[] content, String mimeType) {
		try {
			this.s3Client.putObject(PutObjectRequest.builder()
					.bucket(this.properties.storage().bucket())
					.key(key)
					.contentType(mimeType)
					.build(), RequestBody.fromBytes(content));
			return "s3://" + this.properties.storage().bucket() + "/" + key;
		}
		catch (S3Exception | IllegalArgumentException ex) {
			throw new KycException(KycErrorCode.KYC_STORAGE_FAILED);
		}
	}

	@Override
	public byte[] get(String encryptedStorageKey) {
		try {
			S3Location location = location(encryptedStorageKey);
			ResponseBytes<GetObjectResponse> response = this.s3Client.getObjectAsBytes(GetObjectRequest.builder()
					.bucket(location.bucket())
					.key(location.key())
					.build());
			return response.asByteArray();
		}
		catch (NoSuchKeyException ex) {
			throw new KycException(KycErrorCode.KYC_DOCUMENT_NOT_FOUND);
		}
		catch (S3Exception | IllegalArgumentException ex) {
			throw new KycException(KycErrorCode.KYC_STORAGE_FAILED);
		}
	}

	@Override
	public void delete(String encryptedStorageKey) {
		try {
			S3Location location = location(encryptedStorageKey);
			this.s3Client.deleteObject(DeleteObjectRequest.builder()
					.bucket(location.bucket())
					.key(location.key())
					.build());
		}
		catch (S3Exception | IllegalArgumentException ex) {
			throw new KycException(KycErrorCode.KYC_STORAGE_FAILED);
		}
	}

	private S3Location location(String encryptedStorageKey) {
		if (encryptedStorageKey == null || encryptedStorageKey.isBlank()) {
			throw new IllegalArgumentException("Missing KYC storage key");
		}
		if (encryptedStorageKey.startsWith("s3://")) {
			String withoutScheme = encryptedStorageKey.substring("s3://".length());
			int slash = withoutScheme.indexOf('/');
			if (slash <= 0 || slash == withoutScheme.length() - 1) {
				throw new IllegalArgumentException("Invalid S3 KYC storage key");
			}
			return new S3Location(withoutScheme.substring(0, slash), withoutScheme.substring(slash + 1));
		}
		return new S3Location(this.properties.storage().bucket(), encryptedStorageKey);
	}

	private record S3Location(String bucket, String key) {
	}
}
