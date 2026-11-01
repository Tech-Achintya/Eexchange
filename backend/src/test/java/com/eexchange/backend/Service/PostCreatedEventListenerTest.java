package com.eexchange.backend.Service;

import com.eexchange.backend.Entity.Category;
import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.ListingType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Service.Event.PostCreatedEvent;
import com.eexchange.backend.Service.Event.PostCreatedEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostCreatedEventListenerTest {

    private PostRepository postRepository;
    private ModerationService moderationService;
    private StorageService storageService;
    private PostCreatedEventListener listener;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        moderationService = mock(ModerationService.class);
        storageService = mock(StorageService.class);

        listener = new PostCreatedEventListener(
                postRepository, moderationService, storageService);

        when(storageService.publicUrl(any())).thenAnswer(i -> "https://cdn.test/" + i.getArgument(0));
    }

    @Test
    void stubApprovesPendingPost() {
        // Use the real stub so this test also proves the stub's behaviour.
        listener = new PostCreatedEventListener(
                postRepository, new StubModerationService(), storageService);

        Post post = pendingPost();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        listener.onPostCreated(new PostCreatedEvent(1L));

        assertThat(post.getStatus()).isEqualTo(PostStatus.APPROVED);
        assertThat(post.getRejectionReason()).isNull();
        verify(postRepository).save(post);
    }

    @Test
    void rejectionSetsStatusAndReason() {
        Post post = pendingPost();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(moderationService.review(any()))
                .thenReturn(new ModerationService.ModerationResult(false, "Prohibited item"));

        listener.onPostCreated(new PostCreatedEvent(1L));

        assertThat(post.getStatus()).isEqualTo(PostStatus.REJECTED);
        assertThat(post.getRejectionReason()).isEqualTo("Prohibited item");
    }

    @Test
    void missingPostIsIgnored() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        listener.onPostCreated(new PostCreatedEvent(99L));

        verify(moderationService, never()).review(any());
        verify(postRepository, never()).save(any());
    }

    private Post pendingPost() {
        Post post = Post.builder()
                .id(1L)
                .title("Used textbook")
                .description("A fine textbook for CS students")
                .listingType(ListingType.SELL)
                .condition(ItemCondition.GOOD)
                .category(Category.builder().id(5L).name("Books").build())
                .status(PostStatus.PENDING_REVIEW)
                .build();

        post.getImages().add(PostImage.builder()
                .post(post).url("posts/1/a.jpg").position((short) 0).build());
        return post;
    }
}