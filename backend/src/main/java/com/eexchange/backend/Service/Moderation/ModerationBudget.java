package com.eexchange.backend.Service.Moderation;

import com.eexchange.backend.Configuration.ModerationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

// One Redis counter per UTC day, so a restart never resets the free-tier budget.
@Component
@RequiredArgsConstructor
public class ModerationBudget {

    private static final String KEY_PREFIX = "moderation:calls:";
    private static final Duration TTL = Duration.ofDays(2);

    private final StringRedisTemplate redisTemplate;
    private final ModerationProperties properties;

    public String currentKey() {
        return KEY_PREFIX + LocalDate.now(ZoneOffset.UTC);
    }

    public boolean tryConsume() {
        String key = currentKey();
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            return false;
        }
        if (count == 1L) {
            redisTemplate.expire(key, TTL);
        }
        return count <= properties.getDailyBudget();
    }

    public boolean isExhausted() {
        String raw = redisTemplate.opsForValue().get(currentKey());
        if (raw == null) {
            return false;
        }
        try {
            return Long.parseLong(raw) >= properties.getDailyBudget();
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}