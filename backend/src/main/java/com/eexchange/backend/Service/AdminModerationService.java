package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Response.AdminModerationQueueItemResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.PostImageRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Service.Moderation.BlockedUserPolicy;
import com.eexchange.backend.Utils.HtmlEscaper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
public class AdminModerationService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int REASON_MIN = 5;
    private static final int REASON_MAX = 300;

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final AuditService auditService;
    private final EmailService emailService;
    private final StorageService storageService;

    // ---------------------------------------------------------------- queue

    @Transactional(readOnly = true)
    public PageResponse<AdminModerationQueueItemResponse> queue(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));

        Page<Post> posts = postRepository.findManualReviewQueue(
                PostStatus.MANUAL_REVIEW, pageable);

        if (posts.getContent().isEmpty()) {
            return pageResponse(posts, List.of());
        }

        List<Long> postIds = posts.getContent().stream().map(Post::getId).toList();
        Map<Long, List<String>> imageUrls = postImageRepository
                .findByPostIdInOrderByPostIdAscPositionAsc(postIds)
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getPost().getId(),
                        Collectors.mapping(
                                image -> storageService.publicUrl(image.getUrl()),
                                Collectors.toList())));

        List<AdminModerationQueueItemResponse> content = posts.getContent().stream()
                .map(post -> toQueueItem(
                        post, imageUrls.getOrDefault(post.getId(), List.of())))
                .toList();

        return pageResponse(posts, content);
    }

    // --------------------------------------------------------------- approve

    @Transactional
    public void approve(Long adminId, Long postId, String note) {
        Post post = requireManualReview(postId);

        boolean blocked = BlockedUserPolicy.isEffectivelyBlocked(post.getUser());

        PostStatus target = blocked ? PostStatus.HIDDEN : PostStatus.APPROVED;
        String hiddenReason = blocked ? BlockedUserPolicy.USER_BLOCKED_REASON : null;

        int updated = postRepository.approveFromManualReview(
                postId, target, hiddenReason,
                PostStatus.MANUAL_REVIEW, Instant.now());

        if (updated == 0) {
            throw noLongerAwaiting();
        }

        String trimmedNote = trimToNull(note);

        auditService.log(adminId, AuditAction.APPROVE_POST, AuditTargetType.POST,
                postId, trimmedNote, previousStatusMetadata());

        queueOwnerEmail(post,
                (email, title) -> emailService.sendManualReviewApprovedEmail(
                        email,
                        HtmlEscaper.escape(title),
                        HtmlEscaper.escape(trimmedNote),
                        !blocked));
    }

    // ---------------------------------------------------------------- reject

    @Transactional
    public void reject(Long adminId, Long postId, String reason) {
        String trimmedReason = requireReason(reason);
        Post post = requireManualReview(postId);

        int updated = postRepository.rejectFromManualReview(
                postId, PostStatus.REJECTED, trimmedReason,
                PostStatus.MANUAL_REVIEW, Instant.now());

        if (updated == 0) {
            throw noLongerAwaiting();
        }

        auditService.log(adminId, AuditAction.REJECT_POST, AuditTargetType.POST,
                postId, trimmedReason, previousStatusMetadata());

        queueOwnerEmail(post,
                (email, title) -> emailService.sendManualReviewRejectedEmail(
                        email,
                        HtmlEscaper.escape(title),
                        HtmlEscaper.escape(trimmedReason)));
    }

    // --------------------------------------------------------------- requeue

    @Transactional
    public void requeue(Long adminId, Long postId, String note) {
        int updated = postRepository.requeueFromManualReview(
                postId, PostStatus.PENDING_REVIEW,
                PostStatus.MANUAL_REVIEW, Instant.now());

        if (updated == 0) {
            throw noLongerAwaiting();
        }

        // Requeue sends no email: it only asks the AI to try again.
        auditService.log(adminId, AuditAction.REQUEUE_POST, AuditTargetType.POST,
                postId, trimToNull(note), previousStatusMetadata());
    }

    // --------------------------------------------------------------- helpers

    private Post requireManualReview(Long postId) {
        Post post = postRepository.findAdminDetailById(postId).orElse(null);

        if (post == null || post.getStatus() != PostStatus.MANUAL_REVIEW) {
            throw noLongerAwaiting();
        }
        return post;
    }

    private AdminModerationQueueItemResponse toQueueItem(Post post, List<String> imageUrls) {
        Map<String, Object> review = post.getAiReviewResult();

        return new AdminModerationQueueItemResponse(
                post.getId(),
                post.getTitle(),
                post.getPrice(),
                post.getUser() == null ? null : post.getUser().getId(),
                post.getUser() == null ? null : post.getUser().getName(),
                post.getCollege() == null ? null : post.getCollege().getId(),
                post.getCollege() == null ? null : post.getCollege().getName(),
                post.getCreatedAt(),
                queuedAt(post, review),
                stringValue(review, "lastFailure"),
                intValue(review, "attempts"),
                imageUrls);
    }

    private Instant queuedAt(Post post, Map<String, Object> review) {
        Object raw = review == null ? null : review.get("queuedAt");
        if (raw != null) {
            try {
                return Instant.parse(raw.toString());
            } catch (RuntimeException ignored) {
                // Fall through to updatedAt.
            }
        }
        return post.getUpdatedAt();
    }

    private String stringValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : value.toString();
    }

    private Integer intValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String requireReason(String raw) {
        String reason = raw == null ? "" : raw.trim();
        if (reason.length() < REASON_MIN || reason.length() > REASON_MAX) {
            throw new AppException("Reason must be between 5 and 300 characters",
                    HttpStatus.BAD_REQUEST);
        }
        return reason;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, Object> previousStatusMetadata() {
        return Map.of("previousStatus", PostStatus.MANUAL_REVIEW.name());
    }

    private AppException noLongerAwaiting() {
        return new AppException("Post is no longer awaiting manual review",
                HttpStatus.CONFLICT);
    }

    private void queueOwnerEmail(Post post, OwnerEmail action) {
        String email = post.getUser() == null ? null : post.getUser().getEmail();
        String title = post.getTitle();

        if (email == null || email.isBlank()) {
            return;
        }

        afterCommit(() -> action.send(email, title));
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

    @FunctionalInterface
    private interface OwnerEmail {
        void send(String email, String title);
    }
}
