package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.InterestCreateRequest;
import com.eexchange.backend.DTO.Response.InterestReceivedResponse;
import com.eexchange.backend.DTO.Response.InterestSentResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
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
import com.eexchange.backend.Utils.HtmlEscaper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterestService {

    private static final int MESSAGE_MIN = 5;
    private static final int MESSAGE_MAX = 1000;
    private static final int CONTACT_MIN = 5;
    private static final int CONTACT_MAX = 255;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final InterestMessageRepository interestMessageRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostImageRepository postImageRepository;
    private final StorageService storageService;
    private final EmailService emailService;

    // --------------------------------------------------------------- create

    @Transactional
    public void create(Long senderId, Long postId, InterestCreateRequest request) {
        User sender = requireUser(senderId);
        String message = validateMessage(request.getMessage());
        String contactInfo = validateContactInfo(request.getContactInfo());

        Post post = postRepository.findDetailById(postId)
                .orElseThrow(this::postNotFound);

        if (post.getUser().getId().equals(senderId)) {
            throw new AppException(
                    "You cannot express interest in your own post",
                    HttpStatus.BAD_REQUEST);
        }

        if (!isVisibleTo(post, sender)) {
            throw postNotFound();
        }

        if (interestMessageRepository.existsByPostIdAndSenderId(postId, senderId)) {
            throw alreadyInterested();
        }

        InterestMessage entity = InterestMessage.builder()
                .post(post)
                .sender(sender)
                .message(message)
                .contactInfo(contactInfo)
                .isRead(false)
                .build();

        try {
            interestMessageRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            // Unique constraint race: two requests at the same moment.
            throw alreadyInterested();
        }

        String ownerEmail = post.getUser().getEmail();
        String senderName = sender.getName();
        String postTitle = post.getTitle();

        // Email only after commit, and never let email failures fail the request.
        afterCommit(() -> {
            try {
                emailService.sendInterestEmail(
                        ownerEmail,
                        HtmlEscaper.escape(senderName),
                        HtmlEscaper.escape(message),
                        HtmlEscaper.escape(contactInfo),
                        HtmlEscaper.escape(postTitle));
            } catch (RuntimeException ex) {
                // Never log the owner's email address.
                log.warn("Interest email failed for post {}", postId, ex);
            }
        });
    }

    // ------------------------------------------------------------- received

    @Transactional(readOnly = true)
    public PageResponse<InterestReceivedResponse> received(Long ownerId,
                                                           boolean unreadOnly,
                                                           int page,
                                                           int size) {
        requireUser(ownerId);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<InterestMessage> messages = interestMessageRepository.findReceived(
                ownerId, PostStatus.DELETED, unreadOnly, pageable);

        if (messages.getContent().isEmpty()) {
            return pageResponse(messages, List.of());
        }

        List<Long> postIds = messages.getContent().stream()
                .map(message -> message.getPost().getId())
                .distinct()
                .toList();

        // ONE batched query for every first image on the page.
        Map<Long, String> firstImageKeys = postImageRepository
                .findFirstImagesByPostIds(postIds)
                .stream()
                .collect(Collectors.toMap(
                        image -> image.getPost().getId(),
                        PostImage::getUrl,
                        (first, ignored) -> first));

        List<InterestReceivedResponse> content = messages.getContent().stream()
                .map(message -> new InterestReceivedResponse(
                        message.getId(),
                        message.getPost().getId(),
                        message.getPost().getTitle(),
                        firstImageKeys.containsKey(message.getPost().getId())
                                ? storageService.publicUrl(
                                firstImageKeys.get(message.getPost().getId()))
                                : null,
                        message.getSender().getName(),
                        message.getMessage(),
                        message.getContactInfo(),
                        message.getIsRead(),
                        message.getCreatedAt()))
                .toList();

        return pageResponse(messages, content);
    }

    // ---------------------------------------------------------------- sent

    @Transactional(readOnly = true)
    public PageResponse<InterestSentResponse> sent(Long senderId, int page, int size) {
        requireUser(senderId);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<InterestMessage> messages = interestMessageRepository.findSent(
                senderId, pageable);

        List<InterestSentResponse> content = messages.getContent().stream()
                .map(message -> new InterestSentResponse(
                        message.getId(),
                        message.getPost().getId(),
                        message.getPost().getTitle(),
                        message.getPost().getStatus(),
                        message.getMessage(),
                        message.getCreatedAt()))
                .toList();

        return pageResponse(messages, content);
    }

    // ----------------------------------------------------------- mark read

    @Transactional
    public void markRead(Long ownerId, Long messageId) {
        InterestMessage message = interestMessageRepository.findDetailById(messageId)
                .orElseThrow(this::messageNotFound);

        if (!message.getPost().getUser().getId().equals(ownerId)) {
            // Do not reveal that the message exists.
            throw messageNotFound();
        }

        // Idempotent: already read means no write.
        if (!Boolean.TRUE.equals(message.getIsRead())) {
            message.setIsRead(true);
            interestMessageRepository.save(message);
        }
    }

    // --------------------------------------------------------- unread count

    @Transactional(readOnly = true)
    public long unreadCount(Long ownerId) {
        requireUser(ownerId);
        return interestMessageRepository.countUnreadForOwner(
                ownerId, PostStatus.DELETED);
    }

    // -------------------------------------------------------------- helpers

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(
                        "Authentication required", HttpStatus.UNAUTHORIZED));
    }

    private boolean isVisibleTo(Post post, User sender) {
        boolean sameCollege = post.getCollege().getId()
                .equals(sender.getCollege().getId());
        boolean approved = post.getStatus() == PostStatus.APPROVED;
        boolean notExpired = post.getExpiresAt() == null
                || post.getExpiresAt().isAfter(Instant.now());
        return sameCollege && approved && notExpired;
    }

    private String validateMessage(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < MESSAGE_MIN || value.length() > MESSAGE_MAX) {
            throw new AppException(
                    "Message must be between 5 and 1000 characters",
                    HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String validateContactInfo(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < CONTACT_MIN || value.length() > CONTACT_MAX) {
            throw new AppException(
                    "Contact info must be between 5 and 255 characters",
                    HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private AppException postNotFound() {
        return new AppException("Post not found", HttpStatus.NOT_FOUND);
    }

    private AppException messageNotFound() {
        return new AppException("Interest message not found", HttpStatus.NOT_FOUND);
    }

    private AppException alreadyInterested() {
        return new AppException(
                "You have already expressed interest in this post",
                HttpStatus.CONFLICT);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private <T> PageResponse<T> pageResponse(Page<?> page, List<T> content) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}