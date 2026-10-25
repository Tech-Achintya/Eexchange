package com.eexchange.backend.configuration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RedisRateLimiter {

    private static final DefaultRedisScript<Long> INCR_WITH_EXPIRE = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitResult consume(String key, int limit, int windowSeconds) {
        Long count = redisTemplate.execute(
                INCR_WITH_EXPIRE,
                List.of(key),
                String.valueOf(windowSeconds)
        );

        long current = count == null ? 0 : count;

        if (current <= limit) {
            return new RateLimitResult(true, 0);
        }

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        long retryAfter = (ttl == null || ttl <= 0) ? windowSeconds : ttl;
        return new RateLimitResult(false, retryAfter);
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
    }
}