package com.eexchange.backend.Configuration;

import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

public final class RedisLuaScripts {

    private RedisLuaScripts() {
        // Utility class
    }

    /**
     * Atomically increments a counter and ensures an expiration TTL is set if the key is new
     * or previously had no expiration (-1).
     *
     * KEYS[1] = Counter key
     * ARGV[1] = Expiration TTL in seconds
     * Returns: The updated counter value (Long)
     */
    public static final DefaultRedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('TTL', KEYS[1]) == -1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class
    );

    /**
     * Atomically increments a hash field and refreshes the hash expiration TTL.
     * <p>
     * Used for per-identifier/per-IP login failure counters: the hash key is the
     * account identifier, each field is a source IP.
     * <p>
     * KEYS[1] = Hash key
     * ARGV[1] = Hash field
     * ARGV[2] = Expiration TTL in seconds
     * Returns: The updated counter value (Long)
     */
    public static final DefaultRedisScript<Long> HINCRBY_WITH_TTL = new DefaultRedisScript<>(
            """
            local count = redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
            if count == 1 or redis.call('TTL', KEYS[1]) == -1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return count
            """,
            Long.class
    );

    /**
     * Atomically increments a counter, ensures expiration TTL, and returns both the updated
     * count and remaining TTL in a single round-trip.
     *
     * KEYS[1] = Rate limit key
     * ARGV[1] = Window in seconds
     * Returns: List of two Longs: [currentCount, remainingTtlSeconds]
     */
    @SuppressWarnings("rawtypes")
    public static final DefaultRedisScript<List> RATE_LIMIT_WITH_TTL = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('TTL', KEYS[1]) == -1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return {count, redis.call('TTL', KEYS[1])}
            """,
            List.class
    );

    /**
     * Atomically releases a distributed lock only if the current value matches the lock token.
     * Prevents an instance from inadvertently releasing another instance's expired lock.
     *
     * KEYS[1] = Lock key
     * ARGV[1] = Lock owner token (e.g. UUID)
     * Returns: 1 if deleted, 0 if token mismatch or key did not exist
     */
    public static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
            Long.class
    );
}
