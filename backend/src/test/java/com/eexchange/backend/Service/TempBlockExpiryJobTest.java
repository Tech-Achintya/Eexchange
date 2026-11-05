package com.eexchange.backend.Service;

import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TempBlockExpiryJobTest {

    private UserRepository userRepository;
    private AdminUserService adminUserService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private TempBlockExpiryJob job;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        adminUserService = mock(AdminUserService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("job:tempblock-expiry"), eq("1"), eq(Duration.ofSeconds(55))))
                .thenReturn(true);

        job = new TempBlockExpiryJob(userRepository, adminUserService, redisTemplate);
    }

    @Test
    void unblocksExpiredUsersAndReleasesLock() {
        when(userRepository.findExpiredTempBlocks(
                eq(UserStatus.TEMP_BLOCKED), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(
                        User.builder().id(1L).build(),
                        User.builder().id(2L).build()));

        when(adminUserService.unblockExpired(eq(1L), any(Instant.class))).thenReturn(true);
        when(adminUserService.unblockExpired(eq(2L), any(Instant.class))).thenReturn(true);

        job.run();

        verify(adminUserService).unblockExpired(eq(1L), any(Instant.class));
        verify(adminUserService).unblockExpired(eq(2L), any(Instant.class));
        verify(redisTemplate).delete("job:tempblock-expiry");
    }

    @Test
    void skipsWhenAnotherInstanceHoldsTheLock() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        job.run();

        verifyNoInteractions(userRepository, adminUserService);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void continuesWhenConditionalUpdateChangedNothing() {
        when(userRepository.findExpiredTempBlocks(any(), any(), any()))
                .thenReturn(List.of(
                        User.builder().id(1L).build(),
                        User.builder().id(2L).build()));

        when(adminUserService.unblockExpired(eq(1L), any(Instant.class))).thenReturn(false);
        when(adminUserService.unblockExpired(eq(2L), any(Instant.class))).thenReturn(true);

        job.run();

        verify(adminUserService).unblockExpired(eq(1L), any(Instant.class));
        verify(adminUserService).unblockExpired(eq(2L), any(Instant.class));
    }

    @Test
    void queriesOnlyExpiredTempBlocksInBatchOfOneHundred() {
        when(userRepository.findExpiredTempBlocks(any(), any(), any()))
                .thenReturn(List.of());

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        job.run();

        verify(userRepository).findExpiredTempBlocks(
                eq(UserStatus.TEMP_BLOCKED), now.capture(), pageable.capture());

        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(now.getValue()).isNotNull();
    }

    @Test
    void releasesLockWhenProcessingFails() {
        when(userRepository.findExpiredTempBlocks(any(), any(), any()))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThatThrownBy(() -> job.run())
                .isInstanceOf(RuntimeException.class);

        verify(redisTemplate).delete("job:tempblock-expiry");
    }
}