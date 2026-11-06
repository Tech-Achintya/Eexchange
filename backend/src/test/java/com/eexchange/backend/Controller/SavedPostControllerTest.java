package com.eexchange.backend.Controller;

import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.SavedPostRepository;
import com.eexchange.backend.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SavedPostControllerTest {

    private SavedPostRepository savedPostRepository;
    private PostRepository postRepository;
    private SavedPostController controller;

    @BeforeEach
    void setUp() {
        savedPostRepository = mock(SavedPostRepository.class);
        postRepository = mock(PostRepository.class);
        controller = new SavedPostController(savedPostRepository, postRepository,
                mock(UserRepository.class));
    }

    @Test
    void manualReviewPostCannotBeSaved() {
        Post post = Post.builder()
                .id(5L)
                .status(PostStatus.MANUAL_REVIEW)
                .college(College.builder().id(10L).name("College A").build())
                .user(User.builder().id(7L).name("Owner").build())
                .build();

        when(savedPostRepository.existsByUserIdAndPostId(7L, 5L)).thenReturn(false);
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));

        AppException ex = assertThrows(AppException.class,
                () -> controller.toggleSavePost(5L, new UserPrincipal(7L, "u@c.edu", 10L)));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(savedPostRepository, never()).insertIfAbsent(7L, 5L);
    }
}
