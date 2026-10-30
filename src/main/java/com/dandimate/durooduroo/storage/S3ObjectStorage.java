/** 운영 환경에서 사용하는 S3 호환 Object Storage 구현입니다. 업로드·조회 모두 presigned URL로 브라우저가 직접 처리한다. */
package com.dandimate.durooduroo.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3ObjectStorage(@Value("${storage.aws-region:ap-northeast-2}") String region, @Value("${storage.aws-bucket}") String bucket,
                           @Value("${storage.aws-endpoint:}") String endpoint) {
        var builder = S3Client.builder().region(Region.of(region));
        var presignerBuilder = S3Presigner.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
            presignerBuilder.endpointOverride(URI.create(endpoint)).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        this.client = builder.build();
        this.presigner = presignerBuilder.build();
        this.bucket = bucket;
    }

    @Override
    public String locationFor(String key) {
        return "s3://" + bucket + "/" + key;
    }

    @Override
    public void putBytes(String key, byte[] bytes, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(), RequestBody.fromBytes(bytes));
    }

    @Override
    public void delete(String location) {
        keyOf(location).ifPresent(key -> client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()));
    }

    @Override
    public void deletePrefix(String keyPrefix) {
        String token = null;
        do {
            var page = client.listObjectsV2(software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
                    .bucket(bucket).prefix(keyPrefix).continuationToken(token).build());
            page.contents().forEach(object -> client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(object.key()).build()));
            token = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (token != null);
    }

    @Override
    public Optional<String> presignedPutUrl(String key, String contentType, Duration ttl) {
        var request = PutObjectPresignRequest.builder().signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build()).build();
        return Optional.of(presigner.presignPutObject(request).url().toString());
    }

    @Override
    public Optional<StoredObject> head(String location) {
        return keyOf(location).flatMap(key -> {
            try {
                var head = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
                return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
            } catch (NoSuchKeyException e) {
                return Optional.empty();
            } catch (S3Exception e) {
                if (e.statusCode() == 404) return Optional.empty();
                throw e;
            }
        });
    }

    @Override
    public Optional<String> presignedGetUrl(String location, Duration ttl) {
        return keyOf(location).map(key -> presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build()).build()).url().toString());
    }

    @Override
    public Optional<Resource> open(String location) {
        return Optional.empty();
    }

    private Optional<String> keyOf(String location) {
        String prefix = "s3://" + bucket + "/";
        return location != null && location.startsWith(prefix) ? Optional.of(location.substring(prefix.length())) : Optional.empty();
    }
}
