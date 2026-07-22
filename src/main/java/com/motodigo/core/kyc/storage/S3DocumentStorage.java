package com.motodigo.core.kyc.storage;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import com.motodigo.core.kyc.config.KycProperties;
import com.motodigo.core.kyc.exception.KycErrorCode;
import com.motodigo.core.kyc.exception.KycException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class S3DocumentStorage implements DocumentStorage {

	private final KycProperties.Storage properties;
	private final S3Client s3Client;
	private final AtomicBoolean bucketReady = new AtomicBoolean(false);

	public S3DocumentStorage(KycProperties properties) {
		this.properties = properties.storage();
		this.s3Client = S3Client.builder()
				.endpointOverride(URI.create(this.properties.endpoint()))
				.region(Region.of(this.properties.region()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(this.properties.accessKey(), this.properties.secretKey())))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(this.properties.pathStyle())
						.build())
				.build();
	}

	@Override
	public String put(String objectKey, byte[] encryptedContent, String mimeType) {
		try {
			ensureBucket();
			this.s3Client.putObject(PutObjectRequest.builder()
							.bucket(this.properties.bucket())
							.key(objectKey)
							.contentType("application/octet-stream")
							.metadata(java.util.Map.of("original-content-type", mimeType))
							.build(),
					RequestBody.fromBytes(encryptedContent));
			return "s3://" + this.properties.bucket() + "/" + objectKey;
		}
		catch (S3Exception ex) {
			throw new KycException(KycErrorCode.KYC_STORAGE_FAILED, ex);
		}
	}

	@Override
	public byte[] get(String encryptedStorageKey) {
		try {
			String objectKey = objectKey(encryptedStorageKey);
			ResponseBytes<?> bytes = this.s3Client.getObject(GetObjectRequest.builder()
							.bucket(this.properties.bucket())
							.key(objectKey)
							.build(),
					ResponseTransformer.toBytes());
			return bytes.asByteArray();
		}
		catch (S3Exception ex) {
			throw new KycException(KycErrorCode.KYC_STORAGE_FAILED, ex);
		}
	}

	private void ensureBucket() {
		if (this.bucketReady.get()) {
			return;
		}
		try {
			this.s3Client.headBucket(HeadBucketRequest.builder().bucket(this.properties.bucket()).build());
		}
		catch (NoSuchBucketException ex) {
			this.s3Client.createBucket(CreateBucketRequest.builder().bucket(this.properties.bucket()).build());
		}
		catch (S3Exception ex) {
			if (ex.statusCode() == 404) {
				this.s3Client.createBucket(CreateBucketRequest.builder().bucket(this.properties.bucket()).build());
			}
			else {
				throw ex;
			}
		}
		this.bucketReady.set(true);
	}

	private String objectKey(String encryptedStorageKey) {
		String prefix = "s3://" + this.properties.bucket() + "/";
		if (!encryptedStorageKey.startsWith(prefix)) {
			throw new KycException(KycErrorCode.KYC_STORAGE_FAILED);
		}
		return encryptedStorageKey.substring(prefix.length());
	}
}
