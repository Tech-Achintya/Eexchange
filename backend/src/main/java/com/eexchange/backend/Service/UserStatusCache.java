package com.eexchange.backend.Service;

import com.eexchange.backend.Entity.Enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class UserStatusCache {

    public static final String KEY_PREFIX = "user:status:";
    public static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    public static String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    public String get(Long userId) {
        return redisTemplate.opsForValue().get(key(userId));
    }

    public void put(Long userId, UserStatus status) {
        redisTemplate.opsForValue().set(key(userId), status.name(), TTL);
    }

    public void evict(Long userId) {
        redisTemplate.delete(key(userId));
    }
}