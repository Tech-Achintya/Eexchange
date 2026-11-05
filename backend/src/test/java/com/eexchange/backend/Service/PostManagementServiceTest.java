package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.PostUpdateRequest;
import com.eexchange.backend.DTO.Response.PostResponse;
import com.eexchange.backend.Entity.Category;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.CategoryRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Service.Event.PostCreatedEvent;
import com.eexchange.backend.Service.Storage.ImageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.web.multipart.MultipartFile;

class PostManagementServiceTest {

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'
    };

    private UserRepository userRepository;
    private CategoryRepository categoryRepository;
    private PostRepository postRepository;
    private StorageService storageService;
    private ApplicationEventPublisher eventPublisher;
    private PostService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        postRepository = mock(PostRepository.class);
        storageService = mock(StorageService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new PostService(userRepository, categoryRepository,
                postRepository, storageService, eventPublisher);

        ReflectionTestUtils.setField(service, "expiryDays", 60L);
        ReflectionTestUtils.setField(service, "maxImages", 5);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L)));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category()));
        when(storageService.store(anyLong(), any(byte[].class), any(ImageType.class)))
                .thenAnswer(i -> "posts/7/new-" + System.nanoTime() + ".jpg");
        when(storageService.publicUrl(any()))
                .thenAnswer(i -> "https://cdn.test/" + i.getArgument(0));
        when(postRepository.saveAndFlush(any(Post.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------ update

    @Test
    void updateByNonOwnerReturns404() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 99L, PostStatus.APPROVED)));

        AppException ex = assertThrows(AppException.class,
                () -> service.updatePost(7L, 1L, updateRequest(), List.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(storageService);
    }

    @Test
    void updateOnSoldPostReturns400() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 7L, PostStatus.SOLD)));

        AppException ex = assertThrows(AppException.class,
                () -> service.updatePost(7L, 1L, updateRequest(), List.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("no longer be edited");
    }

    @Test
    void updateResetsStatusAndRepublishesEvent() {
        Post post = post(1L, 7L, PostStatus.APPROVED);
        post.setRejectionReason("old reason");
        when(postRepository.findDetailById(1L)).thenReturn(Optional.of(post));

        PostResponse response = service.updatePost(7L, 1L, updateRequest(), List.of());

        assertThat(response.status()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(post.getRejectionReason()).isNull();
        assertThat(post.getTitle()).isEqualTo("Updated title");
        verify(eventPublisher).publishEvent(new PostCreatedEvent(1L));
    }

    @Test
    void updateRejectsForeignImageId() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 7L, PostStatus.APPROVED)));

        PostUpdateRequest request = updateRequest();
        request.setRemoveImageIds(List.of(999L));

        AppException ex = assertThrows(AppException.class,
                () -> service.updatePost(7L, 1L, request, List.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("do not belong");
        verifyNoInteractions(storageService);
    }

    @Test
    void updateRejectsRemovingAllImages() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 7L, PostStatus.APPROVED)));

        PostUpdateRequest request = updateRequest();
        request.setRemoveImageIds(List.of(100L, 101L));

        AppException ex = assertThrows(AppException.class,
                () -> service.updatePost(7L, 1L, request, List.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("at least one image");
    }

    @Test
    void updateRejectsMoreThanFiveImages() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 7L, PostStatus.APPROVED)));

        // 2 existing + 4 new = 6
        AppException ex = assertThrows(AppException.class,
                () -> service.updatePost(7L, 1L, updateRequest(), fourImages()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("at most 5");
        verify(storageService, never()).store(anyLong(), any(), any());
    }

    @Test
    void removedFilesAreDeletedOnlyAfterCommit() {
        Post post = post(1L, 7L, PostStatus.APPROVED);
        when(postRepository.findDetailById(1L)).thenReturn(Optional.of(post));

        PostUpdateRequest request = updateRequest();
        request.setRemoveImageIds(List.of(100L));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updatePost(7L, 1L, request, List.of());

            // Not yet: the transaction has not committed.
            verify(storageService, never()).delete("posts/7/a.jpg");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(storageService).delete("posts/7/a.jpg");
            verify(storageService, never()).delete("posts/7/b.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rollbackDeletesNewlyUploadedFilesOnly() {
        Post post = post(1L, 7L, PostStatus.APPROVED);
        when(postRepository.findDetailById(1L)).thenReturn(Optional.of(post));
        when(storageService.store(anyLong(), any(byte[].class), any(ImageType.class)))
                .thenReturn("posts/7/new.jpg");

        PostUpdateRequest request = updateRequest();
        request.setRemoveImageIds(List.of(100L));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updatePost(7L, 1L, request, List.of(image("new.jpg")));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(storageService).delete("posts/7/new.jpg");
            // The removed file must survive a rollback.
            verify(storageService, never()).delete("posts/7/a.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void storageDeleteFailureDoesNotFailTheRequest() {
        Post post = post(1L, 7L, PostStatus.APPROVED);
        when(postRepository.findDetailById(1L)).thenReturn(Optional.of(post));

        PostUpdateRequest request = updateRequest();
        request.setRemoveImageIds(List.of(100L));

        org.mockito.Mockito.doThrow(new AppException("S3 down", HttpStatus.BAD_GATEWAY))
                .when(storageService).delete("posts/7/a.jpg");

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> service.updatePost(7L, 1L, request, List.of()));

            assertDoesNotThrow(() -> TransactionSynchronizationManager
                    .getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // -------------------------------------------------------------- sold

    @Test
    void markSoldOnlyFromApproved() {
        Post approved = post(1L, 7L, PostStatus.APPROVED);
        when(postRepository.findDetailById(1L)).thenReturn(Optional.of(approved));

        PostResponse response = service.markSold(7L, 1L);

        assertThat(response.status()).isEqualTo(PostStatus.SOLD);

        Post pending = post(2L, 7L, PostStatus.PENDING_REVIEW);
        when(postRepository.findDetailById(2L)).thenReturn(Optional.of(pending));

        AppException ex = assertThrows(AppException.class,
                () -> service.markSold(7L, 2L));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void markSoldByNonOwnerReturns404() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 99L, PostStatus.APPROVED)));

        assertThat(assertThrows(AppException.class,
                () -> service.markSold(7L, 1L)).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------ delete

    @Test
    void deleteSoftDeletesAndRemovesFilesAfterCommit() {
        Post post = post(1L, 7L, PostStatus.APPROVED);
        when(postRepository.findDetailById(1L)).thenReturn(Optional.of(post));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deletePost(7L, 1L);

            assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
            verify(storageService, never()).delete(any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(storageService).delete("posts/7/a.jpg");
            verify(storageService).delete("posts/7/b.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteByNonOwnerReturns404AndKeepsPost() {
        Post post = post(1L, 99L, PostStatus.APPROVED);
        when(postRepository.findDetailById(1L)).thenReturn(Optional.of(post));

        assertThat(assertThrows(AppException.class,
                () -> service.deletePost(7L, 1L)).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post.getStatus()).isEqualTo(PostStatus.APPROVED);
        verify(storageService, never()).delete(any());
    }

    // ---------------------------------------------------------- fixtures

    private User user(Long id) {
        return User.builder()
                .id(id)
                .name("Owner")
                .college(College.builder().id(10L).name("College A").build())
                .build();
    }

    private Category category() {
        return Category.builder().id(5L).name("Books").slug("books").isActive(true).build();
    }

    private Post post(Long id, Long ownerId, PostStatus status) {
        Post post = Post.builder()
                .id(id)
                .user(User.builder().id(ownerId).name("Owner").build())
                .college(College.builder().id(10L).name("College A").build())
                .category(category())
                .title("Original title")
                .description("Original description text")
                .price(new BigDecimal("100.00"))
                .condition(ItemCondition.GOOD)
                .status(status)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();

        post.getImages().add(PostImage.builder()
                .id(100L).post(post).url("posts/7/a.jpg").position((short) 0).build());
        post.getImages().add(PostImage.builder()
                .id(101L).post(post).url("posts/7/b.jpg").position((short) 1).build());
        return post;
    }

    private PostUpdateRequest updateRequest() {
        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle("Updated title");
        request.setDescription("Updated description text");
        request.setCategoryId(5L);
        request.setPrice(new BigDecimal("150.00"));
        request.setCondition(ItemCondition.LIKE_NEW);
        return request;
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile("newImages", name, "image/jpeg", JPEG_BYTES);
    }

    private List<MultipartFile> fourImages() {
        return List.of(image("1.jpg"), image("2.jpg"), image("3.jpg"), image("4.jpg"));
    }
}