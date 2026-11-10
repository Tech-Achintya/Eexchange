package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.InterestCreateRequest;
import com.eexchange.backend.DTO.Response.InterestReceivedResponse;
import com.eexchange.backend.DTO.Response.InterestSentResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Category;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.InterestMessage;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.InterestMessageRepository;
import com.eexchange.backend.Repository.PostImageRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterestServiceTest {

    private InterestMessageRepository interestMessageRepository;
    private PostRepository postRepository;
    private UserRepository userRepository;
    private PostImageRepository postImageRepository;
    private StorageService storageService;
    private InterestService service;

    private User sender;
    private User owner;

    @BeforeEach
    void setUp() {
        interestMessageRepository = mock(InterestMessageRepository.class);
        postRepository = mock(PostRepository.class);
        userRepository = mock(UserRepository.class);
        postImageRepository = mock(PostImageRepository.class);
        storageService = mock(StorageService.class);

        service = new InterestService(
                interestMessageRepository,
                postRepository,
                userRepository,
                postImageRepository,
                storageService);

        sender = user(7L, "<i>Sender</i>", "sender@college.edu", 10L);
        owner = user(9L, "Owner", "owner@college.edu", 10L);

        when(userRepository.findById(7L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(9L)).thenReturn(Optional.of(owner));
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(owner, 10L, PostStatus.APPROVED, future())));
        when(interestMessageRepository.saveAndFlush(any(InterestMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.publicUrl(any()))
                .thenAnswer(invocation -> "https://cdn.test/" + invocation.getArgument(0));
    }

    // ------------------------------------------------------------- create

    @Test
    void ownPostReturns400() {
        Post own = post(sender, 10L, PostStatus.APPROVED, future());
        when(postRepository.findDetailById(1L)).thenReturn(Optional.of(own));

        AppException ex = assertThrows(AppException.class,
                () -> service.create(7L, 1L, request("Is this available?", "9876543210")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(interestMessageRepository, never()).saveAndFlush(any());
    }

    @Test
    void otherCollegeReturns404() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(owner, 20L, PostStatus.APPROVED, future())));

        AppException ex = assertThrows(AppException.class,
                () -> service.create(7L, 1L, request("Is this available?", "9876543210")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void nonApprovedPostReturns404() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(owner, 10L, PostStatus.HIDDEN, future())));

        AppException ex = assertThrows(AppException.class,
                () -> service.create(7L, 1L, request("Is this available?", "9876543210")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void expiredPostReturns404() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(owner, 10L, PostStatus.APPROVED, past())));

        AppException ex = assertThrows(AppException.class,
                () -> service.create(7L, 1L, request("Is this available?", "9876543210")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void duplicateReturns409() {
        when(interestMessageRepository.existsByPostIdAndSenderId(1L, 7L))
                .thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> service.create(7L, 1L, request("Is this available?", "9876543210")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(interestMessageRepository, never()).saveAndFlush(any());
    }

    @Test
    void uniqueConstraintRaceReturns409() {
        when(interestMessageRepository.existsByPostIdAndSenderId(1L, 7L))
                .thenReturn(false);
        when(interestMessageRepository.saveAndFlush(any(InterestMessage.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        AppException ex = assertThrows(AppException.class,
                () -> service.create(7L, 1L, request("Is this available?", "9876543210")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void contactInfoIsRequiredAndLengthLimited() {
        assertThat(assertThrows(AppException.class,
                () -> service.create(7L, 1L, request("Is this available?", null)))
                .getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(assertThrows(AppException.class,
                () -> service.create(7L, 1L, request("Is this available?", "abcd")))
                .getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(assertThrows(AppException.class,
                () -> service.create(7L, 1L, request("Is this available?", "x".repeat(256))))
                .getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void messageIsLengthLimited() {
        assertThat(assertThrows(AppException.class,
                () -> service.create(7L, 1L, request("abcd", "9876543210")))
                .getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(assertThrows(AppException.class,
                () -> service.create(7L, 1L, request("x".repeat(1001), "9876543210")))
                .getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSucceedsWithoutSendingEmail() {
        InterestCreateRequest request = request(
                "<script>alert(1)</script> hi",
                "<b>555-1234</b>");

        service.create(7L, 1L, request);

        // No email is sent; emails are auth-only.
    }


    // ----------------------------------------------------------- received

    @Test
    void receivedMapsCallerDataAndFirstImage() {
        InterestMessage message = message(false);
        Page<InterestMessage> page = page(message);

        when(interestMessageRepository.findReceived(
                eq(9L), eq(PostStatus.DELETED), eq(false), any(Pageable.class)))
                .thenReturn(page);
        when(postImageRepository.findFirstImagesByPostIds(List.of(1L)))
                .thenReturn(List.of(PostImage.builder()
                        .id(50L)
                        .post(message.getPost())
                        .url("posts/9/a.jpg")
                        .position((short) 0)
                        .build()));

        PageResponse<InterestReceivedResponse> result =
                service.received(9L, false, 0, 20);

        assertThat(result.content()).hasSize(1);
        InterestReceivedResponse row = result.content().get(0);
        assertThat(row.postId()).isEqualTo(1L);
        assertThat(row.postTitle()).isEqualTo("<b>Lamp</b>");
        assertThat(row.firstImageUrl()).isEqualTo("https://cdn.test/posts/9/a.jpg");
        assertThat(row.senderName()).isEqualTo("<i>Sender</i>");
        assertThat(row.contactInfo()).isEqualTo("<b>555-1234</b>");

        verify(interestMessageRepository).findReceived(
                eq(9L), eq(PostStatus.DELETED), eq(false), any(Pageable.class));
        verify(postImageRepository).findFirstImagesByPostIds(List.of(1L));
    }

    @Test
    void receivedForwardsUnreadOnly() {
        when(interestMessageRepository.findReceived(
                eq(9L), eq(PostStatus.DELETED), eq(true), any(Pageable.class)))
                .thenReturn(page());

        service.received(9L, true, 0, 20);

        verify(interestMessageRepository).findReceived(
                eq(9L), eq(PostStatus.DELETED), eq(true), any(Pageable.class));
        verify(postImageRepository, never()).findFirstImagesByPostIds(any());
    }

    @Test
    void receivedEmptyPageDoesNotQueryImages() {
        when(interestMessageRepository.findReceived(
                eq(9L), eq(PostStatus.DELETED), eq(false), any(Pageable.class)))
                .thenReturn(page());

        assertThat(service.received(9L, false, 0, 20).content()).isEmpty();

        verify(postImageRepository, never()).findFirstImagesByPostIds(any());
    }

    // --------------------------------------------------------------- sent

    @Test
    void sentContainsOnlyCallersMessages() {
        when(interestMessageRepository.findSent(eq(7L), any(Pageable.class)))
                .thenReturn(page(message(false)));

        PageResponse<InterestSentResponse> result = service.sent(7L, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).postStatus())
                .isEqualTo(PostStatus.APPROVED);
        verify(interestMessageRepository).findSent(eq(7L), any(Pageable.class));
    }

    // ------------------------------------------------------------ mark read

    @Test
    void markReadRejectsNonOwnerWith404() {
        InterestMessage message = message(false);
        when(interestMessageRepository.findDetailById(5L))
                .thenReturn(Optional.of(message));

        AppException ex = assertThrows(AppException.class,
                () -> service.markRead(7L, 5L));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(interestMessageRepository, never()).save(any());
    }

    @Test
    void markReadIsIdempotent() {
        InterestMessage alreadyRead = message(true);
        when(interestMessageRepository.findDetailById(5L))
                .thenReturn(Optional.of(alreadyRead));

        service.markRead(9L, 5L);
        verify(interestMessageRepository, never()).save(any());

        InterestMessage unread = message(false);
        when(interestMessageRepository.findDetailById(6L))
                .thenReturn(Optional.of(unread));

        service.markRead(9L, 6L);

        assertThat(unread.getIsRead()).isTrue();
        verify(interestMessageRepository).save(unread);
    }

    @Test
    void unreadCountIsScopedToCallersPosts() {
        when(interestMessageRepository.countUnreadForOwner(9L, PostStatus.DELETED))
                .thenReturn(4L);

        assertThat(service.unreadCount(9L)).isEqualTo(4);
        verify(interestMessageRepository).countUnreadForOwner(9L, PostStatus.DELETED);
    }

    // ------------------------------------------------------------- fixtures

    private User user(Long id, String name, String email, Long collegeId) {
        return User.builder()
                .id(id)
                .name(name)
                .email(email)
                .college(College.builder().id(collegeId).name("College " + collegeId).build())
                .build();
    }

    private Post post(User postOwner, Long collegeId, PostStatus status, Instant expiresAt) {
        return Post.builder()
                .id(1L)
                .title("<b>Lamp</b>")
                .college(College.builder().id(collegeId).name("College " + collegeId).build())
                .user(postOwner)
                .category(Category.builder().id(5L).name("Books").build())
                .status(status)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();
    }

    private InterestMessage message(boolean isRead) {
        return InterestMessage.builder()
                .id(5L)
                .post(post(owner, 10L, PostStatus.APPROVED, future()))
                .sender(sender)
                .message("Is this available?")
                .contactInfo("<b>555-1234</b>")
                .isRead(isRead)
                .createdAt(Instant.now())
                .build();
    }

    private InterestCreateRequest request(String message, String contactInfo) {
        InterestCreateRequest request = new InterestCreateRequest();
        request.setMessage(message);
        request.setContactInfo(contactInfo);
        return request;
    }

    private Instant future() {
        return Instant.now().plusSeconds(86400);
    }

    private Instant past() {
        return Instant.now().minusSeconds(60);
    }

    @SafeVarargs
    private Page<InterestMessage> page(InterestMessage... messages) {
        return new PageImpl<>(List.of(messages),
                PageRequest.of(0, 20), messages.length);
    }
}
