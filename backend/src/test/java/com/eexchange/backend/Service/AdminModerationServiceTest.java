package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Response.AdminModerationQueueItemResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.PostImageRepository;
import com.eexchange.backend.Repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminModerationServiceTest {

    private PostRepository postRepository;
    private PostImageRepository postImageRepository;
    private AuditService auditService;
    private EmailService emailService;
    private StorageService storageService;
    private AdminModerationService service;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        postImageRepository = mock(PostImageRepository.class);
        auditService = mock(AuditService.class);
        emailService = mock(EmailService.class);
        storageService = mock(StorageService.class);

        service = new AdminModerationService(postRepository, postImageRepository,
                auditService, emailService, storageService);

        when(storageService.publicUrl(any()))
                .thenAnswer(i -> "https://cdn.test/" + i.getArgument(0));
        when(postImageRepository.findByPostIdInOrderByPostIdAscPositionAsc(anyCollection()))
                .thenReturn(List.of());
    }

    @Test
    void queueReturnsManualReviewRowsWithQueuedAtAndImageUrls() {
        Post post = manualPost();
        post.setAiReviewResult(Map.of(
                "lastFailure", "TIMEOUT",
                "attempts", 3,
                "queuedAt", "2026-02-01T10:15:30Z"));

        when(postRepository.findManualReviewQueue(
                eq(PostStatus.MANUAL_REVIEW), any(Pageable.class)))
                .thenReturn(page(post));
        when(postImageRepository.findByPostIdInOrderByPostIdAscPositionAsc(anyCollection()))
                .thenReturn(List.of(image(post, "posts/7/a.jpg")));

        PageResponse<AdminModerationQueueItemResponse> result = service.queue(0, 20);

        assertThat(result.content()).hasSize(1);
        AdminModerationQueueItemResponse row = result.content().get(0);
        assertThat(row.id()).isEqualTo(1L);
        assertThat(row.title()).isEqualTo("Lamp & shade");
        assertThat(row.price()).isEqualByComparingTo("100.00");
        assertThat(row.sellerId()).isEqualTo(7L);
        assertThat(row.sellerName()).isEqualTo("Owner");
        assertThat(row.collegeId()).isEqualTo(10L);
        assertThat(row.queuedAt()).isEqualTo(Instant.parse("2026-02-01T10:15:30Z"));
        assertThat(row.lastFailure()).isEqualTo("TIMEOUT");
        assertThat(row.attempts()).isEqualTo(3);
        assertThat(row.imageUrls()).containsExactly("https://cdn.test/posts/7/a.jpg");
    }

    @Test
    void approveUnblockedOwnerApprovesAuditsAndEmailsAfterCommit() {
        Post post = manualPost();
        when(postRepository.findAdminDetailById(1L)).thenReturn(Optional.of(post));
        when(postRepository.approveFromManualReview(
                eq(1L), eq(PostStatus.APPROVED), isNull(),
                eq(PostStatus.MANUAL_REVIEW), any(Instant.class)))
                .thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.approve(1L, 1L, "  Looks good  ");

            verify(postRepository).approveFromManualReview(
                    eq(1L), eq(PostStatus.APPROVED), isNull(),
                    eq(PostStatus.MANUAL_REVIEW), any(Instant.class));
            verify(auditService).log(1L, AuditAction.APPROVE_POST,
                    AuditTargetType.POST, 1L, "Looks good",
                    Map.of("previousStatus", "MANUAL_REVIEW"));
            verify(emailService, never()).sendManualReviewApprovedEmail(
                    any(), any(), any(), any(Boolean.class));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(emailService).sendManualReviewApprovedEmail(
                    "owner@college.edu", "Lamp &amp; shade", "Looks good", true);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void approveBlockedOwnerHidesPostAndUsesNeutralEmail() {
        Post post = manualPost();
        post.getUser().setStatus(UserStatus.PERM_BLOCKED);
        when(postRepository.findAdminDetailById(1L)).thenReturn(Optional.of(post));
        when(postRepository.approveFromManualReview(
                eq(1L), eq(PostStatus.HIDDEN), eq("USER_BLOCKED"),
                eq(PostStatus.MANUAL_REVIEW), any(Instant.class)))
                .thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.approve(1L, 1L, null);

            verify(postRepository).approveFromManualReview(
                    eq(1L), eq(PostStatus.HIDDEN), eq("USER_BLOCKED"),
                    eq(PostStatus.MANUAL_REVIEW), any(Instant.class));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(emailService).sendManualReviewApprovedEmail(
                    "owner@college.edu", "Lamp &amp; shade", "", false);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectSetsReasonAuditsAndEmailsEscapedReason() {
        Post post = manualPost();
        when(postRepository.findAdminDetailById(1L)).thenReturn(Optional.of(post));
        when(postRepository.rejectFromManualReview(
                eq(1L), eq(PostStatus.REJECTED), eq("<b>bad</b> item"),
                eq(PostStatus.MANUAL_REVIEW), any(Instant.class)))
                .thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.reject(1L, 1L, "  <b>bad</b> item  ");

            verify(auditService).log(1L, AuditAction.REJECT_POST,
                    AuditTargetType.POST, 1L, "<b>bad</b> item",
                    Map.of("previousStatus", "MANUAL_REVIEW"));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(emailService).sendManualReviewRejectedEmail(
                    "owner@college.edu", "Lamp &amp; shade",
                    "&lt;b&gt;bad&lt;/b&gt; item");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void requeueResetsAttemptsAuditsAndSendsNoEmail() {
        when(postRepository.requeueFromManualReview(
                eq(1L), eq(PostStatus.PENDING_REVIEW),
                eq(PostStatus.MANUAL_REVIEW), any(Instant.class)))
                .thenReturn(1);

        service.requeue(1L, 1L, "quota reset");

        verify(postRepository).requeueFromManualReview(
                eq(1L), eq(PostStatus.PENDING_REVIEW),
                eq(PostStatus.MANUAL_REVIEW), any(Instant.class));
        verify(auditService).log(1L, AuditAction.REQUEUE_POST,
                AuditTargetType.POST, 1L, "quota reset",
                Map.of("previousStatus", "MANUAL_REVIEW"));
        verify(emailService, never()).sendManualReviewApprovedEmail(
                any(), any(), any(), any(Boolean.class));
        verify(emailService, never()).sendManualReviewRejectedEmail(
                any(), any(), any());
    }

    @Test
    void approveConflictsWhenPostIsNoLongerManualReview() {
        Post post = manualPost();
        post.setStatus(PostStatus.APPROVED);
        when(postRepository.findAdminDetailById(1L)).thenReturn(Optional.of(post));

        AppException ex = assertThrows(AppException.class,
                () -> service.approve(1L, 1L, null));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo("Post is no longer awaiting manual review");
        verify(postRepository, never()).approveFromManualReview(
                any(), any(), any(), any(), any());
    }

    @Test
    void approveConflictsWhenConditionalUpdateAffectsNoRows() {
        when(postRepository.findAdminDetailById(1L))
                .thenReturn(Optional.of(manualPost()));
        when(postRepository.approveFromManualReview(
                any(), any(), any(), any(), any())).thenReturn(0);

        AppException ex = assertThrows(AppException.class,
                () -> service.approve(1L, 1L, null));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectConflictsWhenConditionalUpdateAffectsNoRows() {
        when(postRepository.findAdminDetailById(1L))
                .thenReturn(Optional.of(manualPost()));
        when(postRepository.rejectFromManualReview(
                any(), any(), any(), any(), any())).thenReturn(0);

        assertThat(assertThrows(AppException.class,
                () -> service.reject(1L, 1L, "bad listing"))
                .getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void requeueConflictsWhenConditionalUpdateAffectsNoRows() {
        when(postRepository.requeueFromManualReview(
                any(), any(), any(), any())).thenReturn(0);

        assertThat(assertThrows(AppException.class,
                () -> service.requeue(1L, 1L, null))
                .getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectReasonValidationRunsBeforeAnyWrite() {
        AppException ex = assertThrows(AppException.class,
                () -> service.reject(1L, 1L, "no"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(postRepository, never()).rejectFromManualReview(
                any(), any(), any(), any(), any());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------- fixtures

    private Post manualPost() {
        return Post.builder()
                .id(1L)
                .title("Lamp & shade")
                .price(new BigDecimal("100.00"))
                .status(PostStatus.MANUAL_REVIEW)
                .user(User.builder().id(7L).name("Owner")
                        .email("owner@college.edu").status(UserStatus.ACTIVE).build())
                .college(College.builder().id(10L).name("College A").build())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private PostImage image(Post post, String key) {
        return PostImage.builder().id(1L).post(post).url(key).position((short) 0).build();
    }

    private Page<Post> page(Post... posts) {
        return new PageImpl<>(List.of(posts));
    }
}
