package com.eexchange.backend.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ModerationPropertiesValidator {

    private final ModerationProperties properties;
    private final Environment environment;

    @PostConstruct
    void validate() {
        String provider = properties.getProvider() == null
                ? ""
                : properties.getProvider().trim().toLowerCase();

        switch (provider) {
            case "openrouter" -> {
                List<String> missing = new ArrayList<>();
                if (isBlank(properties.getApiKey())) {
                    missing.add("OPENROUTER_API_KEY");
                }
                if (isBlank(properties.getModel())) {
                    missing.add("OPENROUTER_MODEL");
                }
                if (!missing.isEmpty()) {
                    throw new IllegalStateException(
                            "Moderation provider is openrouter but these are missing: "
                                    + String.join(", ", missing));
                }
            }
            case "stub" -> {
                // A stub that approves everything must never reach production.
                if (!environment.matchesProfiles("dev", "local", "test")) {
                    throw new IllegalStateException(
                            "moderation.provider=stub is only allowed under the dev profile");
                }
            }
            default -> throw new IllegalStateException(
                    "Unknown moderation.provider '" + provider
                            + "'. Use 'openrouter' or 'stub'.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}