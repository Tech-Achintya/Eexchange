package com.eexchange.backend.Configuration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitResult consume(String key, int limit, int windowSeconds) {
        @SuppressWarnings("unchecked")
        List<Long> result = redisTemplate.execute(
                RedisLuaScripts.RATE_LIMIT_WITH_TTL,
                List.of(key),
                String.valueOf(windowSeconds)
        );

        long current = (result != null && !result.isEmpty() && result.get(0) != null)
                ? result.get(0)
                : 0L;

        if (current <= limit) {
            return new RateLimitResult(true, 0);
        }

        long ttl = (result != null && result.size() > 1 && result.get(1) != null)
                ? result.get(1)
                : -1L;
        long retryAfter = (ttl <= 0) ? windowSeconds : ttl;
        return new RateLimitResult(false, retryAfter);
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
    }
}
