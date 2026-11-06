package com.eexchange.backend.Service.Moderation;

import com.eexchange.backend.Configuration.ModerationProperties;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModerationRetryJobTest {

    private PostRepository postRepository;
    private PostModerationProcessor processor;
    private ModerationBudget budget;
    private ModerationProperties properties;
    private ModerationRetryJob job;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        processor = mock(PostModerationProcessor.class);
        budget = mock(ModerationBudget.class);

        properties = new ModerationProperties();
        properties.setMaxAttempts(3);
        properties.setJobBatchSize(5);
        properties.setBackoffMinutes(List.of(5, 15, 60));

        job = new ModerationRetryJob(postRepository, processor, budget, properties);
    }

    @Test
    void respectsTheEscalatingBackoffPerAttempt() {
        when(budget.isExhausted()).thenReturn(false);
        when(postRepository.findPendingForModeration(
                eq(PostStatus.PENDING_REVIEW), eq(3), any(), any(Pageable.class)))
                .thenReturn(List.of(
                        pendingPost(1L, 1, Instant.now().minus(1, ChronoUnit.MINUTES)),
                        pendingPost(2L, 1, Instant.now().minus(6, ChronoUnit.MINUTES)),
                        pendingPost(3L, 2, Instant.now().minus(14, ChronoUnit.MINUTES)),
                        pendingPost(4L, 2, Instant.now().minus(16, ChronoUnit.MINUTES)),
                        pendingPost(5L, 0, Instant.now().minus(1, ChronoUnit.SECONDS)),
                        pendingPost(6L, 4, Instant.now().minus(59, ChronoUnit.MINUTES)),
                        pendingPost(7L, 4, Instant.now().minus(61, ChronoUnit.MINUTES))));

        job.run();

        // 5 min for attempt 1, 15 for attempt 2, 60 (clamped) for later attempts.
        verify(processor).process(2L);
        verify(processor).process(4L);
        verify(processor).process(5L);
        verify(processor).process(7L);
        verify(processor, never()).process(1L);
        verify(processor, never()).process(3L);
        verify(processor, never()).process(6L);
    }

    @Test
    void ignoresManualReviewPostsEvenIfRepositoryReturnsOne() {
        when(budget.isExhausted()).thenReturn(false);
        Post manual = pendingPost(9L, 0, Instant.now().minus(1, ChronoUnit.HOURS));
        manual.setStatus(PostStatus.MANUAL_REVIEW);

        when(postRepository.findPendingForModeration(
                any(), any(Integer.class), any(), any(Pageable.class)))
                .thenReturn(List.of(manual));

        job.run();

        verify(processor, never()).process(9L);
    }

    @Test
    void asksForBatchSizeAndMaxAttempts() {
        when(budget.isExhausted()).thenReturn(false);
        when(postRepository.findPendingForModeration(
                any(), any(Integer.class), any(), any(Pageable.class)))
                .thenReturn(List.of());

        job.run();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findPendingForModeration(
                eq(PostStatus.PENDING_REVIEW), eq(3), any(), pageable.capture());

        org.assertj.core.api.Assertions
                .assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void stopsImmediatelyWhenBudgetIsExhausted() {
        when(budget.isExhausted()).thenReturn(true);

        job.run();

        verifyNoInteractions(postRepository, processor);
    }

    @Test
    void stopsBetweenPostsWhenBudgetRunsOut() {
        when(budget.isExhausted()).thenReturn(false, false, true, true);
        when(postRepository.findPendingForModeration(any(), any(Integer.class),
                any(), any(Pageable.class)))
                .thenReturn(List.of(
                        pendingPost(1L, 0, Instant.now().minus(1, ChronoUnit.MINUTES)),
                        pendingPost(2L, 0, Instant.now().minus(1, ChronoUnit.MINUTES)),
                        pendingPost(3L, 0, Instant.now().minus(1, ChronoUnit.MINUTES))));

        job.run();

        verify(processor, times(1)).process(any());
    }

    // ---------------------------------------------------------- fixtures

    private Post pendingPost(Long id, int attempts, Instant updatedAt) {
        return Post.builder()
                .id(id)
                .status(PostStatus.PENDING_REVIEW)
                .moderationAttempts(attempts)
                .updatedAt(updatedAt)
                .build();
    }
}