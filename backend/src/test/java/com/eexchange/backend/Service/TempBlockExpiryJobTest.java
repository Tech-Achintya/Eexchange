package com.eexchange.backend.Service;

import com.eexchange.backend.Service.Cache.CollegeFeedVersionService;
import com.eexchange.backend.Configuration.RedisLuaScripts;

import com.eexchange.backend.Audit.AuditEventRepository;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.PostImageRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.ReportRepository;
import com.eexchange.backend.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
                eq("job:tempblock-expiry"), anyString(), eq(Duration.ofSeconds(55))))
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
        verify(redisTemplate).execute(
                eq(RedisLuaScripts.RELEASE_LOCK),
                eq(List.of("job:tempblock-expiry")), anyString());
    }

    @Test
    void skipsWhenAnotherInstanceHoldsTheLock() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        job.run();

        verifyNoInteractions(userRepository, adminUserService);
        verify(redisTemplate, never()).execute(any(), anyList(), anyString());
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

        verify(redisTemplate).execute(
                eq(RedisLuaScripts.RELEASE_LOCK),
                eq(List.of("job:tempblock-expiry")), anyString());
    }

    @Test
    void expiredUnblockIsAuditedAsSystemAction() {
        UserRepository users = mock(UserRepository.class);
        PostRepository posts = mock(PostRepository.class);
        AuditService auditService = mock(AuditService.class);

        User expired = User.builder().id(1L).email("u@college.edu").build();
        when(users.findExpiredTempBlocks(
                eq(UserStatus.TEMP_BLOCKED), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(expired));
        when(users.unblockIfExpired(
                eq(1L), eq(UserStatus.ACTIVE), eq(UserStatus.TEMP_BLOCKED),
                any(Instant.class)))
                .thenReturn(1);
        when(users.findById(1L)).thenReturn(Optional.of(expired));

        AdminUserService realService = new AdminUserService(
                users,
                posts,
                mock(PostImageRepository.class),
                mock(ReportRepository.class),
                mock(AuditEventRepository.class),
                auditService,
                new PostMediaCleanup(mock(StorageService.class)),
                mock(UserStatusCache.class),
                mock(EmailService.class),
                mock(CollegeFeedVersionService.class));
        ReflectionTestUtils.setField(realService, "maxTempBlockDays", 90);

        new TempBlockExpiryJob(users, realService, redisTemplate).run();

        verify(auditService).logSystem(
                eq(AuditAction.AUTO_UNBLOCK),
                eq(AuditTargetType.USER),
                eq(1L),
                eq("Temporary block expired"),
                eq(Map.of("postsRestored", 0)));
    }
}