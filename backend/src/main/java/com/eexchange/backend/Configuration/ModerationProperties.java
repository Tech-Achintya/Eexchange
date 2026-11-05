package com.eexchange.backend.Configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "moderation")
@Getter
@Setter
public class ModerationProperties {

    private String provider = "openrouter";
    private String apiKey;
    private String model;
    private String baseUrl = "https://openrouter.ai/api/v1";

    private int timeoutSeconds = 25;
    private int malformedRetries = 1;
    private int dailyBudget = 45;
    private int maxAttempts = 5;
    private int jobBatchSize = 5;
    private int backoffMinutes = 2;

    private long jobDelayMs = 60_000;
}