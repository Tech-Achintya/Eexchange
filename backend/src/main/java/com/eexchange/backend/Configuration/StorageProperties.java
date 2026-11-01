package com.eexchange.backend.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {

    private String publicBaseUrl;
    private S3 s3 = new S3();

    @Getter
    @Setter
    public static class S3 {
        private String bucket;
        private String region;
        private String accessKey;
        private String secretKey;
    }

    @PostConstruct
    void validate() {
        List<String> missing = new ArrayList<>();

        if (isBlank(s3.getBucket())) {
            missing.add("S3_BUCKET");
        }
        if (isBlank(s3.getRegion())) {
            missing.add("S3_REGION");
        }
        if (isBlank(s3.getAccessKey())) {
            missing.add("AWS_ACCESS_KEY_ID");
        }
        if (isBlank(s3.getSecretKey())) {
            missing.add("AWS_SECRET_ACCESS_KEY");
        }
        if (isBlank(publicBaseUrl)) {
            missing.add("STORAGE_PUBLIC_BASE_URL");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required storage environment variables: "
                            + String.join(", ", missing));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}