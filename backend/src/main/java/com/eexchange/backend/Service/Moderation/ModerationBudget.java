package com.eexchange.backend.Service.Moderation;

import com.eexchange.backend.Configuration.ModerationProperties;
import com.eexchange.backend.Configuration.RedisLuaScripts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

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
        Long count = redisTemplate.execute(
                RedisLuaScripts.INCR_WITH_TTL,
                List.of(key),
                String.valueOf(TTL.toSeconds())
        );

        if (count == null) {
            return false;
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
