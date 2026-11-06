package com.eexchange.backend.Service;

import com.eexchange.backend.Audit.AuditActorType;
import com.eexchange.backend.Audit.AuditEventDocument;
import com.eexchange.backend.Audit.AuditEventRepository;
import com.eexchange.backend.DTO.Request.AdminUserBlockRequest;
import com.eexchange.backend.DTO.Request.AdminUserUnblockRequest;
import com.eexchange.backend.DTO.Response.AdminUserDetailResponse;
import com.eexchange.backend.DTO.Response.AdminUserRowResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.ReportStatus;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.PostImageRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.ReportRepository;
import com.eexchange.backend.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private UserRepository userRepository;
    private PostRepository postRepository;
    private PostImageRepository postImageRepository;
    private ReportRepository reportRepository;
    private AuditEventRepository auditEventRepository;
    private AuditService auditService;
    private StorageService storageService;
    private UserStatusCache userStatusCache;
    private EmailService emailService;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        postRepository = mock(PostRepository.class);
        postImageRepository = mock(PostImageRepository.class);
        reportRepository = mock(ReportRepository.class);
        auditEventRepository = mock(AuditEventRepository.class);
        auditService = mock(AuditService.class);
        storageService = mock(StorageService.class);
        userStatusCache = mock(UserStatusCache.class);
        emailService = mock(EmailService.class);

        service = new AdminUserService(
                userRepository,
                postRepository,
                postImageRepository,
                reportRepository,
                auditEventRepository,
                auditService,
                new PostMediaCleanup(storageService),
                userStatusCache,
                emailService);

        ReflectionTestUtils.setField(service, "maxTempBlockDays", 90);

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------- TEMP block

    @Test
    void tempBlockHidesApprovedPostsAndWritesAudit() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(postRepository.hideApprovedByUserId(
                20L, PostStatus.HIDDEN, "USER_BLOCKED", PostStatus.APPROVED))
                .thenReturn(2);

        Instant until = Instant.now().plusSeconds(3600);

        service.block(1L, AdminRole.MODERATOR, 20L,
                blockRequest(AdminUserBlockRequest.Type.TEMP, until, "Repeated spam"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.TEMP_BLOCKED);
        assertThat(user.getBlockedUntil()).isEqualTo(until);
        assertThat(user.getBlockedAt()).isNotNull();
        assertThat(user.getBlockReason()).isEqualTo("Repeated spam");

        verify(postRepository).hideApprovedByUserId(
                20L, PostStatus.HIDDEN, "USER_BLOCKED", PostStatus.APPROVED);

        Map<String, Object> metadata = captureAudit(AuditAction.BLOCK_TEMP, "Repeated spam");
        assertThat(metadata)
                .containsEntry("previousStatus", "ACTIVE")
                .containsEntry("until", until.toString())
                .containsEntry("postsHidden", 2);
    }

    @Test
    void tempBlockRequiresUntil() {
        AppException ex = assertThrows(AppException.class,
                () -> service.block(1L, AdminRole.MODERATOR, 20L,
                        blockRequest(AdminUserBlockRequest.Type.TEMP, null, "Repeated spam")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(auditService);
    }

    @Test
    void tempBlockRejectsPastUntil() {
        AppException ex = assertThrows(AppException.class,
                () -> service.block(1L, AdminRole.MODERATOR, 20L,
                        blockRequest(AdminUserBlockRequest.Type.TEMP,
                                Instant.now().minusSeconds(60), "Repeated spam")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tempBlockRejectsUntilBeyondMax() {
        AppException ex = assertThrows(AppException.class,
                () -> service.block(1L, AdminRole.MODERATOR, 20L,
                        blockRequest(AdminUserBlockRequest.Type.TEMP,
                                Instant.now().plusSeconds(91L * 24 * 3600),
                                "Repeated spam")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tempToTempIsAllowed() {
        User user = user(UserStatus.TEMP_BLOCKED);
        user.setBlockedUntil(Instant.now().plusSeconds(60));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));

        Instant newUntil = Instant.now().plusSeconds(7200);
        service.block(1L, AdminRole.MODERATOR, 20L,
                blockRequest(AdminUserBlockRequest.Type.TEMP, newUntil, "Extended block"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.TEMP_BLOCKED);
        assertThat(user.getBlockedUntil()).isEqualTo(newUntil);
        verify(postRepository).hideApprovedByUserId(
                20L, PostStatus.HIDDEN, "USER_BLOCKED", PostStatus.APPROVED);
    }

    @Test
    void tempToPermIsAllowedAndDeletesPosts() {
        User user = user(UserStatus.TEMP_BLOCKED);
        user.setBlockedUntil(Instant.now().plusSeconds(60));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(postImageRepository.findUrlsByUserId(20L)).thenReturn(List.of("posts/20/a.jpg"));
        when(postRepository.deleteByUserId(20L)).thenReturn(2);

        service.block(1L, AdminRole.SUPER_ADMIN, 20L,
                blockRequest(AdminUserBlockRequest.Type.PERM, null, "Escalated fraud"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.PERM_BLOCKED);
        assertThat(user.getBlockedUntil()).isNull();
        verify(postRepository).deleteByUserId(20L);
        assertThat(captureAudit(AuditAction.BLOCK_PERM, "Escalated fraud"))
                .containsEntry("previousStatus", "TEMP_BLOCKED")
                .containsEntry("postsDeleted", 2);
    }

    // ------------------------------------------------------------- PERM block

    @Test
    void permBlockRequiresSuperAdmin() {
        AppException ex = assertThrows(AppException.class,
                () -> service.block(1L, AdminRole.MODERATOR, 20L,
                        blockRequest(AdminUserBlockRequest.Type.PERM, null, "Fraud account")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(userRepository, never()).findById(anyLong());
        verify(postRepository, never()).deleteByUserId(anyLong());
    }

    @Test
    void permBlockDeletesPostsKeepsUserAndWritesAudit() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(postImageRepository.findUrlsByUserId(20L)).thenReturn(List.of("posts/20/a.jpg"));
        when(postRepository.deleteByUserId(20L)).thenReturn(3);

        service.block(1L, AdminRole.SUPER_ADMIN, 20L,
                blockRequest(AdminUserBlockRequest.Type.PERM, null, "Fraud account"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.PERM_BLOCKED);
        assertThat(user.getBlockedUntil()).isNull();
        assertThat(user.getBlockReason()).isEqualTo("Fraud account");

        verify(userRepository).save(user);
        verify(postRepository).deleteByUserId(20L);

        Map<String, Object> metadata = captureAudit(AuditAction.BLOCK_PERM, "Fraud account");
        assertThat(metadata)
                .containsEntry("previousStatus", "ACTIVE")
                .containsEntry("until", null)
                .containsEntry("postsDeleted", 3);
    }

    @Test
    void permBlockDeletesStorageOnlyAfterCommit() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(postImageRepository.findUrlsByUserId(20L))
                .thenReturn(List.of("posts/20/a.jpg", "posts/20/b.png"));
        when(postRepository.deleteByUserId(20L)).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.block(1L, AdminRole.SUPER_ADMIN, 20L,
                    blockRequest(AdminUserBlockRequest.Type.PERM, null, "Fraud account"));

            verify(storageService, never()).delete(anyString());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(storageService).delete("posts/20/a.jpg");
            verify(storageService).delete("posts/20/b.png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void redisAndEmailHappenOnlyAfterCommit() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));

        Instant until = Instant.now().plusSeconds(3600);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.block(1L, AdminRole.MODERATOR, 20L,
                    blockRequest(AdminUserBlockRequest.Type.TEMP, until, "Spam account"));

            verify(userStatusCache, never()).put(anyLong(), any());
            verify(emailService, never()).sendAccountBlockedEmail(anyString(), anyString(), any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(userStatusCache).put(20L, UserStatus.TEMP_BLOCKED);
            verify(emailService).sendAccountBlockedEmail(
                    "user@college.edu", "Spam account", until);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void permToTempReturns409() {
        User user = user(UserStatus.PERM_BLOCKED);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class,
                () -> service.block(1L, AdminRole.SUPER_ADMIN, 20L,
                        blockRequest(AdminUserBlockRequest.Type.TEMP,
                                Instant.now().plusSeconds(3600), "Downgrade attempt")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(postRepository, never()).hideApprovedByUserId(anyLong(), any(), any(), any());
    }

    @Test
    void blockDeletedUserReturns400() {
        User user = user(UserStatus.ACTIVE);
        user.setDeletedAt(Instant.now());
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class,
                () -> service.block(1L, AdminRole.MODERATOR, 20L,
                        blockRequest(AdminUserBlockRequest.Type.TEMP,
                                Instant.now().plusSeconds(3600), "Spam account")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------------------------------------------------------------- unblock

    @Test
    void unblockRestoresOnlyUserBlockedPostsAndWritesAudit() {
        User user = user(UserStatus.TEMP_BLOCKED);
        user.setBlockedUntil(Instant.now().plusSeconds(3600));
        user.setBlockedAt(Instant.now());
        user.setBlockReason("Repeated spam");
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(postRepository.restoreUserBlockedByUserId(
                20L, PostStatus.APPROVED, PostStatus.HIDDEN, "USER_BLOCKED"))
                .thenReturn(2);

        service.unblock(1L, AdminRole.MODERATOR, 20L, unblockRequest("Appeal accepted"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getBlockedUntil()).isNull();
        assertThat(user.getBlockReason()).isNull();
        assertThat(user.getBlockedAt()).isNull();

        verify(postRepository).restoreUserBlockedByUserId(
                20L, PostStatus.APPROVED, PostStatus.HIDDEN, "USER_BLOCKED");

        Map<String, Object> metadata = captureAudit(AuditAction.UNBLOCK, "Appeal accepted");
        assertThat(metadata)
                .containsEntry("previousStatus", "TEMP_BLOCKED")
                .containsEntry("postsRestored", 2);
    }

    @Test
    void permUnblockRequiresSuperAdmin() {
        User user = user(UserStatus.PERM_BLOCKED);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class,
                () -> service.unblock(1L, AdminRole.MODERATOR, 20L,
                        unblockRequest("Appeal accepted")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(postRepository, never()).restoreUserBlockedByUserId(anyLong(), any(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void unblockActiveReturns409() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class,
                () -> service.unblock(1L, AdminRole.MODERATOR, 20L,
                        unblockRequest("Appeal accepted")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unblockUpdatesRedisAndSendsEmailAfterCommit() {
        User user = user(UserStatus.TEMP_BLOCKED);
        user.setBlockedUntil(Instant.now().plusSeconds(3600));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.unblock(1L, AdminRole.MODERATOR, 20L,
                    unblockRequest("Appeal accepted"));

            verify(userStatusCache, never()).put(anyLong(), any());
            verify(emailService, never()).sendAccountUnblockedEmail(anyString());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(userStatusCache).put(20L, UserStatus.ACTIVE);
            verify(emailService).sendAccountUnblockedEmail("user@college.edu");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void unblockExpiredSkipsWhenConditionalUpdateReturnsZero() {
        when(userRepository.unblockIfExpired(
                eq(20L), eq(UserStatus.ACTIVE), eq(UserStatus.TEMP_BLOCKED), any(Instant.class)))
                .thenReturn(0);

        boolean changed = service.unblockExpired(20L, Instant.now());

        assertThat(changed).isFalse();
        verify(postRepository, never()).restoreUserBlockedByUserId(anyLong(), any(), any(), any());
        verifyNoInteractions(auditService);
        verify(userStatusCache, never()).put(anyLong(), any());
    }

    @Test
    void unblockExpiredWritesSystemAudit() {
        when(userRepository.unblockIfExpired(
                eq(20L), eq(UserStatus.ACTIVE), eq(UserStatus.TEMP_BLOCKED), any(Instant.class)))
                .thenReturn(1);
        when(postRepository.restoreUserBlockedByUserId(
                20L, PostStatus.APPROVED, PostStatus.HIDDEN, "USER_BLOCKED"))
                .thenReturn(2);
        when(userRepository.findById(20L))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        boolean changed = service.unblockExpired(20L, Instant.now());

        assertThat(changed).isTrue();
        verify(auditService).logSystem(
                eq(AuditAction.AUTO_UNBLOCK),
                eq(AuditTargetType.USER),
                eq(20L),
                eq("Temporary block expired"),
                eq(Map.of("postsRestored", 2)));
    }

    // ------------------------------------------------------------- list/detail

    @Test
    void listEscapesWildcardsAndAddsPostCounts() {
        User user = user(UserStatus.ACTIVE);
        Page<User> page = new PageImpl<>(List.of(user), PageRequest.of(0, 100), 1);

        when(userRepository.findForAdmin(
                eq("%50!%!_off%"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        PostRepository.UserPostTotal total = mock(PostRepository.UserPostTotal.class);
        when(total.getUserId()).thenReturn(20L);
        when(total.getTotal()).thenReturn(4L);
        when(postRepository.countByUserIdsExcludingStatus(
                List.of(20L), PostStatus.DELETED)).thenReturn(List.of(total));

        PageResponse<AdminUserRowResponse> result =
                service.list("50%_off", null, null, -1, 999);

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).postCount()).isEqualTo(4);
    }

    @Test
    void detailIncludesStatusCountsAndRecentAudit() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));

        PostRepository.UserPostStatusCount approved =
                mock(PostRepository.UserPostStatusCount.class);
        when(approved.getStatus()).thenReturn(PostStatus.APPROVED);
        when(approved.getTotal()).thenReturn(3L);

        PostRepository.UserPostStatusCount hidden =
                mock(PostRepository.UserPostStatusCount.class);
        when(hidden.getStatus()).thenReturn(PostStatus.HIDDEN);
        when(hidden.getTotal()).thenReturn(1L);

        when(postRepository.countByUserIdGroupByStatus(20L))
                .thenReturn(List.of(approved, hidden));
        when(reportRepository.countByPostOwnerIdAndStatus(20L, ReportStatus.OPEN))
                .thenReturn(2L);

        AuditEventDocument audit = AuditEventDocument.builder()
                .id("99")
                .actorType(AuditActorType.ADMIN)
                .actorId(1L)
                .actorName("root")
                .action(AuditAction.BLOCK_TEMP)
                .targetType(AuditTargetType.USER)
                .targetId(20L)
                .reason("Spam")
                .createdAt(Instant.now())
                .build();
        when(auditEventRepository.findRecentByTarget(
                AuditTargetType.USER, 20L, 10))
                .thenReturn(List.of(audit));

        AdminUserDetailResponse detail = service.detail(20L);

        assertThat(detail.postCountsByStatus())
                .containsEntry(PostStatus.APPROVED, 3L)
                .containsEntry(PostStatus.HIDDEN, 1L)
                .containsEntry(PostStatus.DELETED, 0L);
        assertThat(detail.openReportCount()).isEqualTo(2);
        assertThat(detail.recentAudit()).hasSize(1);
        assertThat(detail.recentAudit().get(0).actorName()).isEqualTo("root");
    }

    // ---------------------------------------------------------------- fixtures

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureAudit(AuditAction action, String reason) {
        ArgumentCaptor<Map<String, Object>> metadata =
                ArgumentCaptor.forClass(Map.class);
        verify(auditService).log(
                eq(1L), eq(action), eq(AuditTargetType.USER), eq(20L), eq(reason),
                metadata.capture());
        return metadata.getValue();
    }

    private User user(UserStatus status) {
        return User.builder()
                .id(20L)
                .email("user@college.edu")
                .name("Target User")
                .college(College.builder().id(10L).name("College A").build())
                .isCollegeVerified(true)
                .status(status)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private AdminUserBlockRequest blockRequest(AdminUserBlockRequest.Type type,
                                               Instant until,
                                               String reason) {
        AdminUserBlockRequest request = new AdminUserBlockRequest();
        request.setType(type);
        request.setUntil(until);
        request.setReason(reason);
        return request;
    }

    private AdminUserUnblockRequest unblockRequest(String reason) {
        AdminUserUnblockRequest request = new AdminUserUnblockRequest();
        request.setReason(reason);
        return request;
    }
}