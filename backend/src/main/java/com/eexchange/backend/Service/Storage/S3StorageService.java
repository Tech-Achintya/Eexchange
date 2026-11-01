package com.eexchange.backend.Service.Storage;

import com.eexchange.backend.Configuration.StorageProperties;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Override
    public String store(Long userId, byte[] content, ImageType imageType) {
        String key = "posts/" + userId + "/" + UUID.randomUUID()
                + "." + imageType.getExtension();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(key)
                .contentType(imageType.getContentType())
                .cacheControl("public, max-age=31536000, immutable")
                .contentLength((long) content.length)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(content));
            return key;
        } catch (SdkException ex) {
            // Details stay in server logs; the client gets no bucket/key/stack trace.
            log.error("S3 upload failed. bucket={}, key={}",
                    properties.getS3().getBucket(), key, ex);
            throw new AppException(
                    "Image storage is temporarily unavailable. Please try again.",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(key)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException ex) {
            log.error("S3 delete failed. bucket={}, key={}",
                    properties.getS3().getBucket(), key, ex);
            throw new AppException(
                    "Image storage is temporarily unavailable. Please try again.",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public String publicUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        String base = properties.getPublicBaseUrl();
        return base.endsWith("/") ? base + key : base + "/" + key;
    }
}