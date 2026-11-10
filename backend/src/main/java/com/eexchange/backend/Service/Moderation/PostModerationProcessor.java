package com.eexchange.backend.Service.Moderation;

import com.eexchange.backend.Configuration.ModerationProperties;
import com.eexchange.backend.Configuration.RedisLuaScripts;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Service.AuditService;
import com.eexchange.backend.Service.Cache.CollegeFeedVersionService;
import com.eexchange.backend.Service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Single entry point used by both the AFTER_COMMIT event and the retry job.
@Service
@RequiredArgsConstructor
@Slf4j
public class PostModerationProcessor {

    private static final String LOCK_PREFIX = "moderation:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(120);

    private final PostRepository postRepository;
    private final StorageService storageService;
    private final ModerationService moderationService;
    private final RejectionMessages rejectionMessages;
    private final ModerationProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final PlatformTransactionManager transactionManager;
    private final AuditService auditService;
    private final CollegeFeedVersionService collegeFeedVersionService;

    // No @Transactional here: the AI call must never hold a DB transaction.
    public void process(Long postId) {
        String lockKey = LOCK_PREFIX + postId;
        String lockToken = UUID.randomUUID().toString();

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, LOCK_TTL);

        if (!Boolean.TRUE.equals(locked)) {
            log.debug("Moderation lock already held for post {}; skipping", postId);
            return;
        }

        try {
            // Short read: capture the content that was sent to the model.
            PostSnapshot snapshot = readSnapshot(postId);
            if (snapshot == null) {
                return;
            }

            // No transaction is open during this call.
            ModerationService.ModerationResult result =
                    moderationService.review(snapshot.context());

            // Short write: re-read under a row lock and apply only if unchanged.
            applyVerdict(postId, snapshot.fingerprint(), result);
        } finally {
            redisTemplate.execute(RedisLuaScripts.RELEASE_LOCK, List.of(lockKey), lockToken);
        }
    }

    private PostSnapshot readSnapshot(Long postId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Post post = postRepository.findById(postId).orElse(null);

            if (post == null) {
                log.debug("Moderation skipped: post {} no longer exists", postId);
                return null;
            }
            if (post.getStatus() != PostStatus.PENDING_REVIEW) {
                log.debug("Moderation skipped: post {} is {}", postId, post.getStatus());
                return null;
            }

            int currentAttempts = post.getModerationAttempts() == null ? 0 : post.getModerationAttempts();
            if (currentAttempts >= properties.getMaxAttempts()) {
                log.warn("Post {} already reached max moderation attempts ({}); skipping LLM", postId, currentAttempts);
                return null;
            }

            List<String> imageKeys = orderedImageKeys(post);
            List<String> imageUrls = imageKeys.stream()
                    .map(storageService::publicUrl)
                    .toList();

            return new PostSnapshot(
                    fingerprint(post.getTitle(), post.getDescription(), imageKeys),
                    new ModerationService.PostModerationContext(
                            post.getId(),
                            post.getTitle(),
                            post.getDescription(),
                            post.getPrice(),
                            imageUrls));
        });
    }

    private void applyVerdict(Long postId,
                              String expectedFingerprint,
                              ModerationService.ModerationResult result) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Post post = postRepository.findByIdForUpdate(postId).orElse(null);

            if (post == null) {
                log.debug("Moderation verdict discarded: post {} no longer exists", postId);
                return;
            }

            if (post.getStatus() != PostStatus.PENDING_REVIEW) {
                log.info("Moderation verdict discarded: post {} is {}",
                        postId, post.getStatus());
                return;
            }

            String currentFingerprint = fingerprint(
                    post.getTitle(), post.getDescription(), orderedImageKeys(post));

            if (!expectedFingerprint.equals(currentFingerprint)) {
                // updatePost() also reset moderationAttempts to 0, so the job
                // will pick the edited post up on its next run.
                log.info("Moderation verdict discarded: post {} changed during review",
                        postId);
                return;
            }

            // Budget exhaustion is not a failed attempt: change nothing at all.
            if (result.outcome() == ModerationService.Outcome.DEFERRED) {
                log.info("Moderation deferred for post {}: daily budget exhausted", post.getId());
                return;
            }

            int attempts = (post.getModerationAttempts() == null
                    ? 0
                    : post.getModerationAttempts()) + 1;

            switch (result.outcome()) {
                case APPROVED -> {
                    if (BlockedUserPolicy.isEffectivelyBlocked(post.getUser())) {
                        post.setStatus(PostStatus.HIDDEN);
                        post.setHiddenReason(BlockedUserPolicy.USER_BLOCKED_REASON);
                    } else {
                        post.setStatus(PostStatus.APPROVED);
                        post.setHiddenReason(null);
                    }
                    post.setRejectionReason(null);
                }
                case REJECTED -> {
                    if (attempts >= properties.getMaxAttempts()) {
                        moveToManualReview(post, result, attempts);
                    } else {
                        post.setStatus(PostStatus.REJECTED);
                        post.setRejectionReason(rejectionMessages.forCode(result.code()));
                    }
                }
                case RETRY_LATER -> {
                    if (attempts >= properties.getMaxAttempts()) {
                        moveToManualReview(post, result, attempts);
                    }
                    // Otherwise deliberately stays PENDING_REVIEW.
                }
                case DEFERRED -> {
                    // Handled above; unreachable here.
                }
            }

            if (post.getStatus() == PostStatus.MANUAL_REVIEW) {
                post.setModerationAttempts(attempts);
                post.setAiReviewResult(manualReviewResult(result, attempts));
            } else {
                post.setModerationAttempts(attempts);
                post.setAiReviewResult(aiReviewResult(result, attempts));
            }

            postRepository.save(post);

            // The college feed contains only APPROVED posts. This post was
            // PENDING_REVIEW when the verdict was applied, so a retry, rejection,
            // hidden result or manual-review move does not change the feed.
            if (post.getStatus() == PostStatus.APPROVED) {
                collegeFeedVersionService.incrementVersion(post.getCollege().getId());
            }

            log.info("Moderation post {} -> {} (attempt {})",
                    post.getId(), result.outcome(), attempts);
        });
    }

    private void moveToManualReview(Post post,
                                    ModerationService.ModerationResult result,
                                    int attempts) {

        boolean wasAlreadyManualReview = (post.getStatus() == PostStatus.MANUAL_REVIEW);

        post.setStatus(PostStatus.MANUAL_REVIEW);

        String lastFailure = result.failure() == null
                ? ModerationService.FailureCategory.HTTP_ERROR.name()
                : result.failure().name();

        log.warn("Post {} reached max moderation attempts ({}) after {}; manual review",
                post.getId(), attempts, lastFailure);

        auditService.logSystem(
                AuditAction.MANUAL_REVIEW_QUEUED,
                AuditTargetType.POST,
                post.getId(),
                "AI review failed after " + attempts + " attempts",
                Map.of("lastFailure", lastFailure, "attempts", attempts));

        // No email sent for manual review queue; emails are auth-only.
    }

    private List<String> orderedImageKeys(Post post) {
        return post.getImages().stream()
                .sorted(Comparator.comparingInt(image ->
                        image.getPosition() == null ? 0 : image.getPosition()))
                .map(PostImage::getUrl)
                .toList();
    }

    private String fingerprint(String title,
                               String description,
                               List<String> imageKeys) {
        StringBuilder builder = new StringBuilder();
        builder.append(title == null ? "" : title).append('\u0000')
                .append(description == null ? "" : description);

        for (String imageKey : imageKeys) {
            builder.append('\u0000').append(imageKey == null ? "" : imageKey);
        }

        return builder.toString();
    }

    // Fixed schema only: no free-form model text ever reaches the DB.
    private Map<String, Object> aiReviewResult(
            ModerationService.ModerationResult result, int attempts) {

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("model", properties.getModel());
        map.put("promptVersion", OpenRouterModerationService.PROMPT_VERSION);
        map.put("verdict", result.outcome().name());
        map.put("code", result.code());
        if (result.failure() != null) {
            // Fixed failure category only; no free-form model text.
            map.put("lastFailure", result.failure().name());
        }
        map.put("attempts", attempts);
        map.put("reviewedAt", Instant.now().toString());
        return map;
    }

    // Manual-review schema: a failure CATEGORY only, never model text.
    private Map<String, Object> manualReviewResult(
            ModerationService.ModerationResult result, int attempts) {

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("model", properties.getModel());
        map.put("promptVersion", OpenRouterModerationService.PROMPT_VERSION);
        map.put("verdict", PostStatus.MANUAL_REVIEW.name());
        map.put("lastFailure", result.failure() == null
                ? ModerationService.FailureCategory.HTTP_ERROR.name()
                : result.failure().name());
        map.put("attempts", attempts);
        map.put("queuedAt", Instant.now().toString());
        return map;
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

    private record PostSnapshot(
            String fingerprint,
            ModerationService.PostModerationContext context) {
    }
}
