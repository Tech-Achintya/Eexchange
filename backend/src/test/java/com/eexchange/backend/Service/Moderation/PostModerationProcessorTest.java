package com.eexchange.backend.Service.Moderation;

import com.eexchange.backend.Configuration.ModerationProperties;
import com.eexchange.backend.Entity.Category;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.ListingType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Service.ModerationService;
import com.eexchange.backend.Service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private PostModerationProcessor processor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        postRepository = mock(PostRepository.class);
        storageService = mock(StorageService.class);
        moderationService = mock(ModerationService.class);
        properties = new ModerationProperties();
        properties.setModel("test/model");
        properties.setMaxAttempts(5);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(storageService.publicUrl(any()))
                .thenAnswer(i -> "https://cdn.test/" + i.getArgument(0));

        processor = new PostModerationProcessor(
                postRepository, storageService, moderationService,
                new RejectionMessages(), properties, redisTemplate);
    }

    @Test
    void approvedTransitionSavesStatusAndAttempts() {
        Post post = pendingPost();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.approved());

        processor.process(1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.APPROVED);
        assertThat(post.getRejectionReason()).isNull();
        assertThat(post.getModerationAttempts()).isEqualTo(1);
        assertThat(post.getAiReviewResult())
                .containsEntry("verdict", "APPROVED")
                .containsEntry("model", "test/model")
                .containsEntry("promptVersion", "v1")
                .containsEntry("attempts", 1)
                .containsKey("reviewedAt");
        verify(postRepository).save(post);
    }

    @Test
    void rejectedTransitionUsesFriendlyMessage() {
        Post post = pendingPost();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.rejected("SCAM"));

        processor.process(1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.REJECTED);
        assertThat(post.getRejectionReason())
                .isEqualTo(new RejectionMessages().forCode("SCAM"))
                .doesNotContain("SCAM");
        assertThat(post.getAiReviewResult()).containsEntry("code", "SCAM");
    }

    @Test
    void retryLaterKeepsPendingAndIncrementsAttempts() {
        Post post = pendingPost();
        post.setModerationAttempts(2);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.retryLater());

        processor.process(1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(post.getModerationAttempts()).isEqualTo(3);
        assertThat(post.getAiReviewResult()).containsEntry("verdict", "RETRY_LATER");
    }

    @Test
    void nonPendingPostIsSkipped() {
        Post post = pendingPost();
        post.setStatus(PostStatus.APPROVED);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

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
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.approved());

        processor.process(1L);

        verify(redisTemplate).delete("moderation:lock:1");
    }

    @Test
    void maxAttemptsIsWarnedOnlyWhenExceeded() {
        Post post = pendingPost();
        post.setModerationAttempts(4);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(moderationService.review(any()))
                .thenReturn(ModerationService.ModerationResult.retryLater());

        processor.process(1L);

        assertThat(post.getModerationAttempts()).isEqualTo(5);
        assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
    }

    // ---------------------------------------------------------- fixtures

    private Post pendingPost() {
        Post post = Post.builder()
                .id(1L)
                .user(User.builder().id(7L).name("Owner").build())
                .college(College.builder().id(10L).name("College A").build())
                .category(Category.builder().id(5L).name("Books").build())
                .title("Cheap laptop")
                .description("Works fine, barely used.")
                .listingType(ListingType.SELL)
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