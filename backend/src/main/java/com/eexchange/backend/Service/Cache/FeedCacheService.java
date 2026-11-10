package com.eexchange.backend.Service.Cache;

import com.eexchange.backend.Configuration.FeedCacheProperties;
import com.eexchange.backend.Configuration.RedisLuaScripts;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.DTO.Response.PostCardResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedCacheService {

    private static final String LOCK_PREFIX = "lock:";

    private final StringRedisTemplate redisTemplate;
    private final FeedCacheKeyBuilder keyBuilder;
    private final CollegeFeedVersionService versionService;
    private final FeedCacheProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Reads the college version once, before the DB load. Callers must pass the same
     * version to {@link #put} so a commit that happens during the request cannot
     * backfill the new version with data from the old snapshot.
     */
    public long version(Long collegeId) {
        return versionService.getVersion(collegeId);
    }

    public Optional<PageResponse<PostCardResponse>> get(long version,
                                                        Long collegeId,
                                                        Long categoryId,
                                                        String q,
                                                        BigDecimal minPrice,
                                                        BigDecimal maxPrice,
                                                        String sort,
                                                        int page,
                                                        int size) {
        Optional<String> keyOpt = keyBuilder.buildKey(
                version, collegeId, categoryId, q, minPrice, maxPrice, sort, page, size);
        if (keyOpt.isEmpty()) {
            return Optional.empty(); // Bypassed (e.g. q is present)
        }

        String key = keyOpt.get();
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            PageResponse<PostCardResponse> response = objectMapper.readValue(
                    json, new TypeReference<PageResponse<PostCardResponse>>() {}
            );
            return Optional.ofNullable(response);
        } catch (Exception ex) {
            log.warn("Redis error reading feed cache for key {}: {}", key, ex.getMessage());
            return Optional.empty(); // Fail-open to DB
        }
    }

    public void put(long version,
                    Long collegeId,
                    Long categoryId,
                    String q,
                    BigDecimal minPrice,
                    BigDecimal maxPrice,
                    String sort,
                    int page,
                    int size,
                    PageResponse<PostCardResponse> pageResponse,
                    Instant earliestExpiry) {

        if (pageResponse == null
                || pageResponse.content() == null
                || pageResponse.content().isEmpty()) {
            return; // Empty page not stored
        }

        Optional<String> keyOpt = keyBuilder.buildKey(
                version, collegeId, categoryId, q, minPrice, maxPrice, sort, page, size);
        if (keyOpt.isEmpty()) {
            return;
        }

        String key = keyOpt.get();

        long ttlSeconds = properties.getDefaultTtlSeconds();
        if (earliestExpiry != null) {
            long remainingSeconds = Duration.between(Instant.now(), earliestExpiry).toSeconds();
            if (remainingSeconds < properties.getMinTtlSeconds()) {
                // Below min-ttl: not stored
                return;
            }
            ttlSeconds = Math.min(ttlSeconds, remainingSeconds);
        }

        if (ttlSeconds <= 0) {
            return;
        }

        String lockKey = LOCK_PREFIX + key;
        String lockToken = UUID.randomUUID().toString();
        try {
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockToken,
                            Duration.ofSeconds(properties.getFillLockTtlSeconds()));
            if (!Boolean.TRUE.equals(locked)) {
                log.debug("Fill lock not acquired for key {}; skipping cache write", key);
                return;
            }

            try {
                String json = objectMapper.writeValueAsString(pageResponse);
                redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
            } finally {
                redisTemplate.execute(RedisLuaScripts.RELEASE_LOCK, List.of(lockKey), lockToken);
            }
        } catch (Exception ex) {
            log.warn("Redis error writing feed cache for key {}: {}", key, ex.getMessage());
            // DB result returned, no exception thrown
        }
    }
}
