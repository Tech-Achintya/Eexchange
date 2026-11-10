package com.eexchange.backend.Service;

import com.eexchange.backend.Configuration.RedisLuaScripts;
import com.eexchange.backend.Exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Failed-login throttling shared by student and admin logins.
 * <p>
 * Failures are counted per identifier <em>and</em> source IP, so an attacker
 * cannot lock a victim out of their account just by spraying wrong passwords
 * from one address. A correct password always wins: callers check the lockout
 * only after the password has been verified, and clear the counter on success.
 */
@Service
@RequiredArgsConstructor
public class LoginLockoutService {

    public static final String SCOPE_USER = "user";
    public static final String SCOPE_ADMIN = "admin";

    private static final String KEY_PREFIX = "login:fail:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.login.max-failures:5}")
    private int maxFailures;

    @Value("${app.login.lockout-seconds:900}")
    private long lockoutSeconds;

    public boolean isLocked(String scope, String identifier, String clientIp) {
        String value = (String) redisTemplate.opsForHash()
                .get(key(scope, identifier), normaliseIp(clientIp));
        return parseCount(value) >= maxFailures;
    }

    public void checkNotLocked(String scope, String identifier, String clientIp) {
        if (isLocked(scope, identifier, clientIp)) {
            long minutes = Math.max(1, lockoutSeconds / 60);
            throw new AppException(
                    "Too many failed logins from this device. Please try again in "
                            + minutes + " minutes.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    public void recordFailure(String scope, String identifier, String clientIp) {
        redisTemplate.execute(
                RedisLuaScripts.HINCRBY_WITH_TTL,
                List.of(key(scope, identifier)),
                normaliseIp(clientIp),
                String.valueOf(lockoutSeconds));
    }

    public void clearFailures(String scope, String identifier) {
        redisTemplate.delete(key(scope, identifier));
    }

    private String key(String scope, String identifier) {
        String normalisedIdentifier = identifier == null
                ? "unknown"
                : identifier.trim().toLowerCase(Locale.ROOT);
        return KEY_PREFIX + scope + ":" + normalisedIdentifier;
    }

    private String normaliseIp(String clientIp) {
        return (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
    }

    private int parseCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
