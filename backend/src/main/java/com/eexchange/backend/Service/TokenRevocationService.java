package com.eexchange.backend.Service;

import com.eexchange.backend.Utils.JWTutil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Revokes every user JWT issued before "now".
 * <p>
 * The cutoff is stored per user and checked by {@code JWTfilter}. It is used by
 * both password change and password reset, so a compromised session cannot
 * survive either flow.
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    public static final String KEY_TOKENS_VALID_AFTER = "user:tokens-valid-after:";

    private final StringRedisTemplate redisTemplate;
    private final JWTutil jwtUtil;

    /**
     * Invalidate all tokens issued before the current second.
     * <p>
     * JJWT serializes "iat" in whole seconds, while raw millis contain a
     * fraction. Flooring the cutoff keeps a token generated in the same second
     * valid (iat == cutoff) while rejecting every older token.
     */
    public void revokeAllTokens(Long userId) {
        long cutoffEpochMillis = (System.currentTimeMillis() / 1000L) * 1000L;
        redisTemplate.opsForValue().set(
                KEY_TOKENS_VALID_AFTER + userId,
                String.valueOf(cutoffEpochMillis),
                Duration.ofMillis(jwtUtil.getExpiryMs()));
    }
}
