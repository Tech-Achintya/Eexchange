package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.DTO.Response.PostCardResponse;
import com.eexchange.backend.DTO.Response.PostDetailResponse;
import com.eexchange.backend.Entity.Category;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.InterestMessageRepository;
import com.eexchange.backend.Repository.PostImageRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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


class PostQueryServiceTest {

    private UserRepository userRepository;
    private PostRepository postRepository;
    private PostImageRepository postImageRepository;
    private StorageService storageService;
    private PostQueryService service;
    private InterestMessageRepository interestMessageRepository;
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        postRepository = mock(PostRepository.class);
        postImageRepository = mock(PostImageRepository.class);
        storageService = mock(StorageService.class);
        interestMessageRepository = mock(InterestMessageRepository.class);

        service = new PostQueryService(
                userRepository,
                postRepository,
                postImageRepository,
                storageService,
                interestMessageRepository);

        when(storageService.publicUrl(any()))
                .thenAnswer(i -> "https://cdn.test/" + i.getArgument(0));
        when(postImageRepository.findFirstImagesByPostIds(anyCollection()))
                .thenReturn(List.of());
        when(postImageRepository.findByPostIdInOrderByPostIdAscPositionAsc(anyCollection()))
                .thenReturn(List.of());
    }

    // ------------------------------------------------------------- feed

    @Test
    void feedQueriesOnlyCallersCollege() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findFeed(eq(10L), eq(PostStatus.APPROVED), any(),
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page(post(1L, 10L, PostStatus.APPROVED, future())));

        service.feed(7L, null, null, null, null, "newest", 0, 12);

        // College A's id only: college B is never queried.
        verify(postRepository).findFeed(eq(10L), eq(PostStatus.APPROVED), any(),
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
        verify(postRepository, never()).findFeed(eq(20L), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void othersUserOnlySeesOthersCollege() {
        when(userRepository.findById(8L)).thenReturn(Optional.of(user(8L, 99L, true)));
        when(postRepository.findFeed(eq(99L), eq(PostStatus.APPROVED), any(),
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page(post(1L, 99L, PostStatus.APPROVED, future())));

        PageResponse<PostCardResponse> result =
                service.feed(8L, null, null, null, null, "newest", 0, 12);

        assertThat(result.content()).hasSize(1);
        verify(postRepository).findFeed(eq(99L), eq(PostStatus.APPROVED), any(),
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void feedPassesApprovedStatusAndNowToHideExpiredAndPendingPosts() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findFeed(any(), any(), any(), any(), any(), any(),
                any(), any(Pageable.class))).thenReturn(page());

        service.feed(7L, null, null, null, null, "newest", 0, 12);

        ArgumentCaptor<PostStatus> status = ArgumentCaptor.forClass(PostStatus.class);
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);

        verify(postRepository).findFeed(any(), status.capture(), now.capture(),
                any(), any(), any(), any(), any(Pageable.class));

        // The SQL keeps only APPROVED and expires_at > now.
        assertThat(status.getValue()).isEqualTo(PostStatus.APPROVED);
        assertThat(now.getValue()).isCloseTo(Instant.now(),
                org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }

    @Test
    void feedEscapesLikeWildcardsAndForwardsFilters() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findFeed(any(), any(), any(), any(), any(), any(),
                any(), any(Pageable.class))).thenReturn(page());

        service.feed(7L, 3L, "50%_off",
                new BigDecimal("10"), new BigDecimal("100"), "priceAsc", 0, 12);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        verify(postRepository).findFeed(eq(10L), eq(PostStatus.APPROVED), any(),
                eq(3L), eq("%50!%!_off%"),
                eq(new BigDecimal("10")), eq(new BigDecimal("100")), pageable.capture());

        Sort sort = pageable.getValue().getSort();
        assertThat(sort.getOrderFor("price"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void maxPriceZeroIsForwardedToReturnOnlyFreeItems() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findFeed(any(), any(), any(), any(), any(), any(),
                any(), any(Pageable.class))).thenReturn(page());

        service.feed(7L, null, null, null, BigDecimal.ZERO, "newest", 0, 12);

        verify(postRepository).findFeed(eq(10L), eq(PostStatus.APPROVED), any(),
                isNull(), isNull(), isNull(), eq(BigDecimal.ZERO), any(Pageable.class));
    }

    @Test
    void priceDescSortOrdersByPriceDescending() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findFeed(any(), any(), any(), any(), any(), any(),
                any(), any(Pageable.class))).thenReturn(page());

        service.feed(7L, null, null, null, null, "priceDesc", 0, 12);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findFeed(any(), any(), any(), any(), any(), any(),
                any(), pageable.capture());

        Sort sort = pageable.getValue().getSort();
        assertThat(sort.getOrderFor("price"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void sizeIsClampedToFiftyAndInvalidSortIsRejected() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findFeed(any(), any(), any(), any(), any(), any(),
                any(), any(Pageable.class))).thenReturn(page());

        service.feed(7L, null, null, null, null, "newest", 0, 999);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findFeed(any(), any(), any(), any(), any(), any(),
                any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);

        AppException ex = assertThrows(AppException.class,
                () -> service.feed(7L, null, null, null, null, "bogus", 0, 12));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ----------------------------------------------------------- detail

    @Test
    void detailReturns404ForOtherCollege() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 20L, PostStatus.APPROVED, future())));

        AppException ex = assertThrows(AppException.class,
                () -> service.detail(7L, 1L));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void detailReturns404ForPendingOrExpiredSameCollegePost() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));

        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 10L, PostStatus.PENDING_REVIEW, future())));
        assertThat(assertThrows(AppException.class, () -> service.detail(7L, 1L))
                .getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        when(postRepository.findDetailById(2L))
                .thenReturn(Optional.of(post(2L, 10L, PostStatus.APPROVED, past())));
        assertThat(assertThrows(AppException.class, () -> service.detail(7L, 2L))
                .getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void ownerSeesOwnPostInAnyStatusExceptDeleted() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(ownedPost(1L, 7L, 10L, PostStatus.PENDING_REVIEW)));

        PostDetailResponse response = service.detail(7L, 1L);

        assertThat(response.status()).isEqualTo(PostStatus.PENDING_REVIEW);

        when(postRepository.findDetailById(2L))
                .thenReturn(Optional.of(ownedPost(2L, 7L, 10L, PostStatus.DELETED)));
        assertThat(assertThrows(AppException.class, () -> service.detail(7L, 2L))
                .getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void detailReturns404WhenPostDoesNotExist() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findDetailById(99L)).thenReturn(Optional.empty());

        assertThat(assertThrows(AppException.class, () -> service.detail(7L, 99L))
                .getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------- mine

    @Test
    void mineQueriesOnlyOwnPostsExcludingDeleted() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findMine(eq(7L), eq(PostStatus.DELETED), isNull(),
                any(Pageable.class)))
                .thenReturn(page(
                        ownedPost(1L, 7L, 10L, PostStatus.APPROVED),
                        ownedPost(2L, 7L, 10L, PostStatus.REJECTED)));

        PageResponse<PostDetailResponse> result =
                service.mine(7L, null, 0, 12);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).allMatch(post -> post.sellerId().equals(7L));
        verify(postRepository).findMine(eq(7L), eq(PostStatus.DELETED), isNull(),
                any(Pageable.class));
    }

    @Test
    void mineForwardsStatusFilterAndSortsNewestFirst() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findMine(any(), any(), any(), any(Pageable.class)))
                .thenReturn(page());

        service.mine(7L, PostStatus.REJECTED, 0, 12);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findMine(eq(7L), eq(PostStatus.DELETED),
                eq(PostStatus.REJECTED), pageable.capture());

        Sort sort = pageable.getValue().getSort();
        assertThat(sort.getOrderFor("createdAt"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void mineIncludesManualReviewPostsAndSupportsStatusFilter() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findMine(eq(7L), eq(PostStatus.DELETED),
                eq(PostStatus.MANUAL_REVIEW), any(Pageable.class)))
                .thenReturn(page(ownedPost(1L, 7L, 10L, PostStatus.MANUAL_REVIEW)));

        PageResponse<PostDetailResponse> result =
                service.mine(7L, PostStatus.MANUAL_REVIEW, 0, 12);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status())
                .isEqualTo(PostStatus.MANUAL_REVIEW);
    }

    @Test
    void ownerCanOpenManualReviewDetailButOthersGet404() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(ownedPost(1L, 7L, 10L, PostStatus.MANUAL_REVIEW)));

        assertThat(service.detail(7L, 1L).status())
                .isEqualTo(PostStatus.MANUAL_REVIEW);

        when(userRepository.findById(8L)).thenReturn(Optional.of(user(8L, 10L, false)));
        assertThat(assertThrows(AppException.class,
                () -> service.detail(8L, 1L)).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void feedOnlyAsksForApprovedPostsNeverManualReview() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findFeed(any(), any(), any(), any(), any(), any(),
                any(), any(Pageable.class))).thenReturn(page());

        service.feed(7L, null, null, null, null, "newest", 0, 12);

        ArgumentCaptor<PostStatus> status = ArgumentCaptor.forClass(PostStatus.class);
        verify(postRepository).findFeed(any(), status.capture(), any(), any(),
                any(), any(), any(), any(Pageable.class));

        assertThat(status.getValue()).isEqualTo(PostStatus.APPROVED);
    }

    @Test
    void deletedUserCannotBrowse() {
        when(userRepository.findById(7L))
                .thenReturn(Optional.of(user(7L, 10L, false, Instant.now())));

        assertThat(assertThrows(AppException.class,
                () -> service.feed(7L, null, null, null, null, "newest", 0, 12))
                .getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------------------------------------------------- fixtures

    private User user(Long id, Long collegeId, boolean others) {
        return user(id, collegeId, others, null);
    }

    private User user(Long id, Long collegeId, boolean others, Instant deletedAt) {
        return User.builder()
                .id(id)
                .name("User " + id)
                .college(College.builder()
                        .id(collegeId)
                        .name(others ? "Others" : "College " + collegeId)
                        .isOthers(others)
                        .build())
                .deletedAt(deletedAt)
                .build();
    }

    private Post post(Long id, Long collegeId, PostStatus status, Instant expiresAt) {
        return Post.builder()
                .id(id)
                .title("Post " + id)
                .description("Description for post " + id)
                .price(new BigDecimal("100.00"))
                .condition(ItemCondition.GOOD)
                .status(status)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .college(College.builder().id(collegeId).name("College " + collegeId).build())
                .user(User.builder().id(99L).name("Someone").build())
                .category(Category.builder().id(5L).name("Books").build())
                .build();
    }

    private Post ownedPost(Long id, Long userId, Long collegeId, PostStatus status) {
        Post post = post(id, collegeId, status, future());
        post.setUser(User.builder().id(userId).name("Owner " + userId).build());
        return post;
    }

    private Instant future() {
        return Instant.now().plus(10, ChronoUnit.DAYS);
    }

    private Instant past() {
        return Instant.now().minus(1, ChronoUnit.DAYS);
    }

    @SafeVarargs
    private <T> Page<T> page(T... items) {
        return new PageImpl<>(List.of(items));
    }

    private Page<Post> page(Post... posts) {
        return new PageImpl<>(List.of(posts));
    }

    @Test
    void feedWithEmptyPageDoesNotQueryImages() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findFeed(any(), any(), any(), any(), any(), any(),
                any(), any(Pageable.class))).thenReturn(page());

        PageResponse<PostCardResponse> result =
                service.feed(7L, null, null, null, null, "newest", 0, 12);

        assertThat(result.content()).isEmpty();
        verify(postImageRepository, never()).findFirstImagesByPostIds(anyCollection());
    }

    @Test
    void mineWithEmptyPageDoesNotQueryImages() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findMine(any(), any(), any(), any(Pageable.class)))
                .thenReturn(page());

        PageResponse<PostDetailResponse> result = service.mine(7L, null, 0, 12);

        assertThat(result.content()).isEmpty();
        verify(postImageRepository, never())
                .findByPostIdInOrderByPostIdAscPositionAsc(anyCollection());
    }
    @Test
    void detailSetsInterestedForNonOwner() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 10L, PostStatus.APPROVED, future())));
        when(interestMessageRepository.existsByPostIdAndSenderId(1L, 7L))
                .thenReturn(true);

        PostDetailResponse response = service.detail(7L, 1L);

        assertThat(response.interested()).isTrue();
        verify(interestMessageRepository).existsByPostIdAndSenderId(1L, 7L);
    }

    @Test
    void detailOwnerIsNeverInterested() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L, false)));
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(ownedPost(1L, 7L, 10L, PostStatus.APPROVED)));

        PostDetailResponse response = service.detail(7L, 1L);

        assertThat(response.interested()).isFalse();
        verify(interestMessageRepository, never())
                .existsByPostIdAndSenderId(any(), any());
    }
}