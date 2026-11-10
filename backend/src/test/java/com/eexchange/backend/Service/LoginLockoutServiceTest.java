package com.eexchange.backend.Service;

import com.eexchange.backend.Configuration.RedisLuaScripts;
import com.eexchange.backend.Exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginLockoutServiceTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private LoginLockoutService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        service = new LoginLockoutService(redisTemplate);
        ReflectionTestUtils.setField(service, "maxFailures", 5);
        ReflectionTestUtils.setField(service, "lockoutSeconds", 900L);
    }

    @Test
    void locksOnlyTheIdentifierAndIpPairAfterTheLimit() {
        String key = "login:fail:user:user@college.edu";
        when(hashOperations.get(key, "203.0.113.7")).thenReturn("5");
        when(hashOperations.get(key, "198.51.100.9")).thenReturn("0");

        assertThat(service.isLocked(LoginLockoutService.SCOPE_USER,
                "User@College.edu", "203.0.113.7")).isTrue();
        // A different address is unaffected: no global account lock.
        assertDoesNotThrow(() -> service.checkNotLocked(LoginLockoutService.SCOPE_USER,
                "user@college.edu", "198.51.100.9"));
    }

    @Test
    void checkNotLockedReturns429OnceThePairIsLocked() {
        String key = "login:fail:admin:admin";
        when(hashOperations.get(key, "203.0.113.7")).thenReturn("5");

        AppException ex = assertThrows(AppException.class,
                () -> service.checkNotLocked(
                        LoginLockoutService.SCOPE_ADMIN, "Admin", "203.0.113.7"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getMessage()).contains("15 minutes");
    }

    @Test
    void recordFailureIncrementsTheScopedHashFieldWithTtl() {
        service.recordFailure(LoginLockoutService.SCOPE_USER, "user@college.edu", "203.0.113.7");

        verify(redisTemplate).execute(
                RedisLuaScripts.HINCRBY_WITH_TTL,
                List.of("login:fail:user:user@college.edu"),
                "203.0.113.7",
                "900");
    }

    @Test
    void clearFailuresDeletesTheWholeIdentifierHash() {
        service.clearFailures(LoginLockoutService.SCOPE_USER, "user@college.edu");

        verify(redisTemplate).delete("login:fail:user:user@college.edu");
    }
}
