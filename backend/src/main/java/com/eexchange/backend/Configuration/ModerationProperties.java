package com.eexchange.backend.Configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
    private int maxAttempts = 3;
    private int jobBatchSize = 5;
    private List<Integer> backoffMinutes = new ArrayList<>(List.of(5, 15, 60));

    private long jobDelayMs = 60_000;
}