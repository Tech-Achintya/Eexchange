package com.eexchange.backend.Configuration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class StorageConfig {

    private final StorageProperties properties;

    @Bean(destroyMethod = "close")
    public S3Client s3Client() {
        StorageProperties.S3 s3 = properties.getS3();

        return S3Client.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                s3.getAccessKey(),
                                s3.getSecretKey())))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .overrideConfiguration(config -> config
                        .apiCallTimeout(Duration.ofSeconds(15))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .build();
    }
}