package com.eexchange.backend.Service.Moderation;

import com.eexchange.backend.Configuration.ModerationProperties;
import com.eexchange.backend.Configuration.RedisLuaScripts;
import com.eexchange.backend.Service.Cache.CollegeFeedVersionService;
import com.eexchange.backend.Entity.Category;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Service.AuditService;
import com.eexchange.backend.Service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostModerationProcessorTest {

    private PostRepository postRepository;
    private StorageService storageService;
    private ModerationService moderationService;
    private ModerationProperties properties;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PlatformTransactionManager transactionManager;
    private AuditService auditService;
    private CollegeFeedVersionService collegeFeedVersionService;
    private AtomicInteger openTransactions;
    private PostModerationProcessor processor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        postRepository = mock(PostRepository.class);
        storageService = mock(StorageService.class);
        moderationService = mock(ModerationService.class);
        properties = new ModerationProperties();
        properties.setModel("test/model");
        properties.setMaxAttempts(3);
        auditService = mock(AuditService.class);
        collegeFeedVersionService = mock(CollegeFeedVersionService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        openTransactions = new AtomicInteger();
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> {
                    openTransactions.incrementAndGet();
                    return new SimpleTransactionStatus();
                });
        doAnswer(invocation -> {
            openTransactions.decrementAndGet();
            return null;
        }).when(transactionManager).commit(any());
        doAnswer(invocation -> {
            openTransactions.decrementAndGet();
            return null;
        }).when(transactionManager).rollback(any());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(storageService.publicUrl(any()))
                .thenAnswer(i -> "https://cdn.test/" + i.getArgument(0));

        processor = new PostModerationProcessor(
                postRepository, storageService, moderationService,
                new RejectionMessages(), properties, redisTemplate,
                transactionManager, auditService,
                collegeFeedVersionService);
    }

    @Test
    void approvedTransitionSavesStatusAndAttempts() {
        Post post = pendingPost();
        givenPost(post);
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.approved());

        processor.process(1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.APPROVED);
        assertThat(post.getRejectionReason()).isNull();
        assertThat(post.getModerationAttempts()).isEqualTo(1);
        assertThat(post.getAiReviewResult())
                .containsEntry("verdict", "APPROVED")
                .containsEntry("model", "test/model")
                .containsEntry("promptVersion", "v2")
                .containsEntry("attempts", 1)
                .containsKey("reviewedAt");
        verify(postRepository).save(post);
        verify(collegeFeedVersionService).incrementVersion(10L);
    }

    @Test
    void rejectedTransitionUsesFriendlyMessage() {
        Post post = pendingPost();
        givenPost(post);
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.rejected("SCAM"));

        processor.process(1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.REJECTED);
        assertThat(post.getRejectionReason())
                .isEqualTo(new RejectionMessages().forCode("SCAM"))
                .doesNotContain("SCAM");
        assertThat(post.getAiReviewResult()).containsEntry("code", "SCAM");
        verify(collegeFeedVersionService, never()).incrementVersion(anyLong());
    }

    @Test
    void retryLaterBeforeMaxAttemptsStaysPendingAndIncrementsAttempts() {
        Post post = pendingPost();
        post.setModerationAttempts(1);
        givenPost(post);
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.retryLater(
                        ModerationService.FailureCategory.HTTP_ERROR));

        processor.process(1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(post.getModerationAttempts()).isEqualTo(2);
        assertThat(post.getAiReviewResult()).containsEntry("verdict", "RETRY_LATER");
        verify(auditService, never()).logSystem(any(), any(), any(), any(), any());
        verify(collegeFeedVersionService, never()).incrementVersion(anyLong());
    }

    @Test
    void thirdFailureMovesPostToManualReviewWithFailureCategoryOnly() {
        Post post = pendingPost();
        post.setModerationAttempts(2);
        givenPost(post);
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.retryLater(
                        ModerationService.FailureCategory.TIMEOUT));

        processor.process(1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.MANUAL_REVIEW);
        assertThat(post.getModerationAttempts()).isEqualTo(3);
        assertThat(post.getAiReviewResult())
                .containsEntry("model", "test/model")
                .containsEntry("promptVersion", "v2")
                .containsEntry("verdict", "MANUAL_REVIEW")
                .containsEntry("lastFailure", "TIMEOUT")
                .containsEntry("attempts", 3)
                .containsKey("queuedAt")
                .doesNotContainKey("code");

        verify(auditService).logSystem(
                eq(AuditAction.MANUAL_REVIEW_QUEUED),
                eq(AuditTargetType.POST),
                eq(1L),
                eq("AI review failed after 3 attempts"),
                eq(Map.of("lastFailure", "TIMEOUT", "attempts", 3)));
        verify(collegeFeedVersionService, never()).incrementVersion(anyLong());
    }

    @Test
    void manualReviewEmailIsSentOnlyAfterCommit() {
        Post post = pendingPost();
        post.setModerationAttempts(2);
        givenPost(post);
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.retryLater(
                        ModerationService.FailureCategory.MALFORMED_ANSWER));

        TransactionSynchronizationManager.initSynchronization();
        try {
            processor.process(1L);

            assertThat(post.getStatus()).isEqualTo(PostStatus.MANUAL_REVIEW);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void manualReviewEmailIsNotSentOnRollback() {
        Post post = pendingPost();
        post.setModerationAttempts(2);
        givenPost(post);
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.retryLater(
                        ModerationService.FailureCategory.HTTP_ERROR));

        TransactionSynchronizationManager.initSynchronization();
        try {
            processor.process(1L);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deferredBudgetExhaustionChangesNothingAndCountsNoAttempt() {
        Post post = pendingPost();
        post.setModerationAttempts(1);
        givenPost(post);
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.deferred());

        processor.process(1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(post.getModerationAttempts()).isEqualTo(1);
        assertThat(post.getAiReviewResult()).isNull();
        verify(postRepository, never()).save(any());
        verify(auditService, never()).logSystem(any(), any(), any(), any(), any());
    }

    @Test
    void nonPendingPostIsSkipped() {
        Post post = pendingPost();
        post.setStatus(PostStatus.APPROVED);
        givenPost(post);

        processor.process(1L);

        verifyNoInteractions(moderationService);
        verify(postRepository, never()).save(any());
    }

    @Test
    void heldLockPreventsDoubleProcessing() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        processor.process(1L);

        verifyNoInteractions(postRepository, moderationService);
    }

    @Test
    void lockIsReleasedAfterProcessing() {
        Post post = pendingPost();
        givenPost(post);
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.approved());

        processor.process(1L);

        verify(redisTemplate).execute(
                eq(RedisLuaScripts.RELEASE_LOCK),
                eq(java.util.List.of("moderation:lock:1")), anyString());
    }

    @Test
    void manualReviewPostIsIgnoredByTheProcessor() {
        Post post = pendingPost();
        post.setStatus(PostStatus.MANUAL_REVIEW);
        givenPost(post);

        processor.process(1L);

        verifyNoInteractions(moderationService);
        verify(postRepository, never()).save(any());
    }

    @Test
    void approvedVerdictForTemporarilyBlockedOwnerIsHiddenAsUserBlocked() {
        Post post = pendingPost();
        post.getUser().setStatus(UserStatus.TEMP_BLOCKED);
        post.getUser().setBlockedUntil(Instant.now().plusSeconds(3600));

        givenPost(post);
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.approved());

        processor.process(1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);
        assertThat(post.getHiddenReason()).isEqualTo("USER_BLOCKED");
        assertThat(post.getRejectionReason()).isNull();
    }

    @Test
    void approvedVerdictForExpiredTempBlockStillApproves() {
        Post post = pendingPost();
        post.getUser().setStatus(UserStatus.TEMP_BLOCKED);
        post.getUser().setBlockedUntil(Instant.now().minusSeconds(60));

        givenPost(post);
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.approved());

        processor.process(1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.APPROVED);
        assertThat(post.getHiddenReason()).isNull();
    }

    @Test
    void postDeletedDuringReviewStaysDeletedAndVerdictIsDiscarded() {
        Post snapshot = pendingPost();
        Post deleted = pendingPost();
        deleted.setStatus(PostStatus.DELETED);

        when(postRepository.findById(1L)).thenReturn(Optional.of(snapshot));
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(deleted));
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.approved());

        processor.process(1L);

        assertThat(deleted.getStatus()).isEqualTo(PostStatus.DELETED);
        assertThat(deleted.getModerationAttempts()).isZero();
        assertThat(deleted.getAiReviewResult()).isNull();
        assertThat(deleted.getRejectionReason()).isNull();
        verify(postRepository, never()).save(any());
    }

    @Test
    void postEditedDuringReviewIsNotApprovedAndStaysPending() {
        Post snapshot = pendingPost();
        Post edited = pendingPost();
        edited.setTitle("Edited title after moderation started");
        edited.setModerationAttempts(0);

        when(postRepository.findById(1L)).thenReturn(Optional.of(snapshot));
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(edited));
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.approved());

        processor.process(1L);

        assertThat(edited.getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(edited.getModerationAttempts()).isZero();
        assertThat(edited.getAiReviewResult()).isNull();
        assertThat(edited.getRejectionReason()).isNull();
        verify(postRepository, never()).save(any());
    }

    @Test
    void reviewRunsOutsideAnyTransaction() throws Exception {
        Post post = pendingPost();
        givenPost(post);
        when(moderationService.review(any())).thenAnswer(invocation -> {
            assertThat(openTransactions.get()).isZero();
            return ModerationService.ModerationResult.approved();
        });

        processor.process(1L);

        assertThat(PostModerationProcessor.class
                .getMethod("process", Long.class)
                .isAnnotationPresent(Transactional.class))
                .isFalse();
        assertThat(post.getStatus()).isEqualTo(PostStatus.APPROVED);
    }

    // ---------------------------------------------------------- fixtures

    private void givenPost(Post post) {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
    }

    private Post pendingPost() {
        Post post = Post.builder()
                .id(1L)
                .user(User.builder().id(7L).name("Owner")
                        .email("owner@college.edu").build())
                .college(College.builder().id(10L).name("College A").build())
                .category(Category.builder().id(5L).name("Books").build())
                .title("Cheap laptop")
                .description("Works fine, barely used.")
                .price(new BigDecimal("100.00"))
                .condition(ItemCondition.GOOD)
                .status(PostStatus.PENDING_REVIEW)
                .moderationAttempts(0)
                .build();

        post.getImages().add(PostImage.builder()
                .id(100L).post(post).url("posts/7/a.jpg").position((short) 0).build());

        return post;
    }
}
