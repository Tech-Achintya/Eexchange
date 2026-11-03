package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.PostCreateRequest;
import com.eexchange.backend.DTO.Response.PostResponse;
import com.eexchange.backend.Entity.Category;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.ListingType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostCreateServiceTest {

    // Real JPEG magic bytes followed by filler. Only the first 3 bytes matter.
    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'
    };

    // "MZ" is the DOS/Windows executable header, not an image.
    private static final byte[] EXE_BYTES = {'M', 'Z', (byte) 0x90, 0x00, 0x03};

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

        service = new PostService(
                userRepository, categoryRepository, postRepository,
                storageService, eventPublisher);

        // @Value fields are not injected by plain construction.
        ReflectionTestUtils.setField(service, "expiryDays", 60L);
        ReflectionTestUtils.setField(service, "maxImages", 5);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category()));
        when(storageService.store(anyLong(), any(byte[].class), any(ImageType.class)))
                .thenReturn("posts/1/uuid.jpg");
        when(storageService.publicUrl(any()))
                .thenAnswer(invocation -> "https://cdn.test/" + invocation.getArgument(0));
    }

    @Test
    void sellWithoutPriceIsRejected() {
        PostCreateRequest request = validRequest(ListingType.SELL);
        request.setPrice(null);

        AppException ex = assertThrows(AppException.class,
                () -> service.createPost(1L, request, List.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(storageService);
    }

    @Test
    void sellWithExchangeWantedIsRejected() {
        PostCreateRequest request = validRequest(ListingType.SELL);
        request.setExchangeWanted("A calculator");

        AppException ex = assertThrows(AppException.class,
                () -> service.createPost(1L, request, List.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void freeWithPriceIsRejected() {
        PostCreateRequest request = validRequest(ListingType.FREE);
        request.setPrice(BigDecimal.TEN);

        AppException ex = assertThrows(AppException.class,
                () -> service.createPost(1L, request, List.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void exchangeWithoutExchangeWantedIsRejected() {
        PostCreateRequest request = validRequest(ListingType.EXCHANGE);
        request.setPrice(null);
        request.setExchangeWanted(null);

        AppException ex = assertThrows(AppException.class,
                () -> service.createPost(1L, request, List.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void exchangeWithPriceIsRejected() {
        PostCreateRequest request = validRequest(ListingType.EXCHANGE);
        request.setPrice(BigDecimal.ONE);
        request.setExchangeWanted("A calculator");

        AppException ex = assertThrows(AppException.class,
                () -> service.createPost(1L, request, List.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void renamedExeIsRejectedByMagicBytes() {
        PostCreateRequest request = validRequest(ListingType.SELL);

        // Content-Type and filename both claim JPEG; the bytes do not.
        MockMultipartFile fakeImage = new MockMultipartFile(
                "images", "photo.jpg", "image/jpeg", EXE_BYTES);

        AppException ex = assertThrows(AppException.class,
                () -> service.createPost(1L, request, List.of(fakeImage)));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("JPEG, PNG or WebP");
        verify(storageService, never()).store(anyLong(), any(), any());
    }

    @Test
    void dbFailureDeletesAlreadyUploadedFiles() {
        PostCreateRequest request = validRequest(ListingType.SELL);
        MockMultipartFile image = new MockMultipartFile(
                "images", "photo.jpg", "image/jpeg", JPEG_BYTES);

        when(storageService.store(anyLong(), any(byte[].class), any(ImageType.class)))
                .thenReturn("posts/1/first.jpg");
        when(postRepository.saveAndFlush(any(Post.class)))
                .thenThrow(new DataIntegrityViolationException("boom"));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.createPost(1L, request, List.of(image)));

        // The uploaded object must not be orphaned.
        verify(storageService).delete("posts/1/first.jpg");
    }

    @Test
    void validPostIsSavedAsPendingReviewAndEventIsPublished() {
        PostCreateRequest request = validRequest(ListingType.SELL);
        MockMultipartFile image = new MockMultipartFile(
                "images", "photo.jpg", "image/jpeg", JPEG_BYTES);

        when(postRepository.saveAndFlush(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(42L);
            return post;
        });

        PostResponse response = service.createPost(1L, request, List.of(image));

        assertThat(response.status()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(response.collegeId()).isEqualTo(10L); // from the DB user, not the request
        assertThat(response.images()).hasSize(1);
        assertThat(response.images().get(0).position()).isZero();
        assertThat(response.expiresAt()).isAfter(response.createdAt() == null
                ? java.time.Instant.now().minusSeconds(1)
                : response.createdAt());
        verify(eventPublisher).publishEvent(new PostCreatedEvent(42L));
    }

    private PostCreateRequest validRequest(ListingType listingType) {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("Used textbook");
        request.setDescription("A fine textbook for CS students");
        request.setCategoryId(5L);
        request.setListingType(listingType);
        request.setCondition(ItemCondition.GOOD);
        if (listingType == ListingType.SELL) {
            request.setPrice(new BigDecimal("100.00"));
        }
        return request;
    }

    private User user() {
        return User.builder()
                .id(1L)
                .name("Alice")
                .college(College.builder().id(10L).name("College A").build())
                .build();
    }

    private Category category() {
        return Category.builder().id(5L).name("Books").slug("books").isActive(true).build();
    }

    @Test
    void postWithoutImagesIsRejected() {
        PostCreateRequest request = validRequest(ListingType.SELL);

        AppException ex = assertThrows(AppException.class,
                () -> service.createPost(1L, request, List.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("At least one image");
        verifyNoInteractions(storageService);
    }
}