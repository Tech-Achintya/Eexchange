package com.eexchange.backend.Service.Moderation;

import com.eexchange.backend.Configuration.ModerationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Slf4j
@ConditionalOnProperty(name = "moderation.provider",
        havingValue = "openrouter", matchIfMissing = true)
public class OpenRouterModerationService implements ModerationService {

    public static final String PROMPT_VERSION = "v2";
    private static final String PROMPT_PATH = "moderation/system-prompt.txt";
    private static final Pattern LISTING_TAG = Pattern.compile("(?i)</?listing>");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ModerationProperties properties;
    private final ModerationBudget budget;
    private final String systemPrompt;

    @Autowired
    public OpenRouterModerationService(ModerationProperties properties,
                                       ModerationBudget budget,
                                       ObjectMapper objectMapper) {
        this(buildRestTemplate(properties), properties, budget, objectMapper, loadPrompt());
    }

    // Package-private for tests: injects a mocked RestTemplate and prompt.
    OpenRouterModerationService(RestTemplate restTemplate,
                                ModerationProperties properties,
                                ModerationBudget budget,
                                ObjectMapper objectMapper,
                                String systemPrompt) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.budget = budget;
        this.objectMapper = objectMapper;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public ModerationResult review(PostModerationContext context) {
        String userMessage = buildUserMessage(
                context.getTitle(), context.getDescription(), context.getPrice());

        // 1 = first try, plus malformedRetries extra tries for malformed answers only.
        int maxTries = 1 + Math.max(0, properties.getMalformedRetries());

        for (int attempt = 1; attempt <= maxTries; attempt++) {
            if (!budget.tryConsume()) {
                log.warn("Moderation daily budget exhausted; post {} stays pending",
                        context.postId());
                // A deferral is not a failed attempt.
                return ModerationResult.deferred();
            }

            Attempt attemptResult = callOnce(context, userMessage);

            if (attemptResult.content().isEmpty()) {
                // 429 / 5xx / timeout / network / unreadable body: no in-call retry.
                return ModerationResult.retryLater(attemptResult.failure());
            }

            Optional<ModerationVerdictParser.Verdict> verdict =
                    ModerationVerdictParser.parse(attemptResult.content().get());

            if (verdict.isPresent()) {
                return verdict.get().approved()
                        ? ModerationResult.approved()
                        : ModerationResult.rejected(verdict.get().code());
            }

            log.warn("Malformed moderation answer for post {} (try {}/{}); "
                            + "answer length {}",
                    context.postId(), attempt, maxTries, attemptResult.content().get().length());
        }

        return ModerationResult.retryLater(FailureCategory.MALFORMED_ANSWER);
    }

    // Empty content means "no usable answer" - caller must not retry in-call.
    private Attempt callOnce(PostModerationContext context, String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey().trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("temperature", 0);
        body.put("max_tokens", 600);
        body.put("reasoning", Map.of("effort", "low"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", buildContentParts(userMessage, context))));

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint(), new HttpEntity<>(body, headers), String.class);

            Optional<String> content = extractContent(response.getBody());
            return content.isPresent()
                    ? new Attempt(content, null)
                    : new Attempt(Optional.empty(), FailureCategory.MALFORMED_ANSWER);
        } catch (HttpStatusCodeException ex) {
            // Status only: never log headers or the body.
            log.error("OpenRouter rejected moderation for post {}: HTTP {}",
                    context.postId(), ex.getStatusCode().value());
            return new Attempt(Optional.empty(), FailureCategory.HTTP_ERROR);
        } catch (RuntimeException ex) {
            log.error("OpenRouter call failed for post {}: {}",
                    context.postId(), ex.getClass().getSimpleName());
            return new Attempt(Optional.empty(), classify(ex));
        }
    }

    private FailureCategory classify(RuntimeException ex) {
        Throwable cause = ex.getCause();
        if (ex instanceof ResourceAccessException
                && (isTimeout(cause) || isTimeout(ex))) {
            return FailureCategory.TIMEOUT;
        }
        return FailureCategory.HTTP_ERROR;
    }

    private boolean isTimeout(Throwable throwable) {
        return throwable instanceof java.net.SocketTimeoutException
                || throwable instanceof java.net.http.HttpTimeoutException
                || throwable instanceof java.io.InterruptedIOException;
    }

    private Optional<String> extractContent(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                return Optional.empty();
            }
            return Optional.of(content.asText());
        } catch (IOException ex) {
            log.warn("OpenRouter returned unreadable JSON");
            return Optional.empty();
        }
    }

    private List<Map<String, Object>> buildContentParts(String userMessage,
                                                        PostModerationContext context) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("type", "text", "text", userMessage));

        for (String imageUrl : context.getImageUrls()) {
            parts.add(Map.of("type", "image_url",
                    "image_url", Map.of("url", imageUrl)));
        }
        return parts;
    }

    private String buildUserMessage(String title, String description, BigDecimal price) {
        return "<listing>\n"
                + "Title: " + neutralize(title) + "\n"
                + "Description: " + neutralize(description) + "\n"
                + "Price: " + formatPrice(price) + "\n"
                + "</listing>";
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "unknown";
        }
        String value = price.stripTrailingZeros().toPlainString();
        return price.signum() == 0 ? "0 (free)" : value;
    }

    // A user must not be able to close the wrapper and inject instructions.
    private String neutralize(String value) {
        if (value == null) {
            return "";
        }
        return LISTING_TAG.matcher(value).replaceAll("&lt;listing&gt;");
    }

    private String endpoint() {
        String base = properties.getBaseUrl() == null
                ? "https://openrouter.ai/api/v1"
                : properties.getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    private static RestTemplate buildRestTemplate(ModerationProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        return new RestTemplate(factory);
    }

    private record Attempt(Optional<String> content, FailureCategory failure) {
    }

    private static String loadPrompt() {
        try (InputStream in = new ClassPathResource(PROMPT_PATH).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Missing classpath resource " + PROMPT_PATH, ex);
        }
    }
}