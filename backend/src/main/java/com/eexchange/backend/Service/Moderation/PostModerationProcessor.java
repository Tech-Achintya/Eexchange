package com.eexchange.backend.Service.Moderation;

import com.eexchange.backend.Configuration.ModerationProperties;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Service.ModerationService;
import com.eexchange.backend.Service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long postId) {
        String lockKey = LOCK_PREFIX + postId;

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);

        if (!Boolean.TRUE.equals(locked)) {
            log.debug("Moderation lock already held for post {}; skipping", postId);
            return;
        }

        try {
            Post post = postRepository.findById(postId).orElse(null);

            if (post == null) {
                log.debug("Moderation skipped: post {} no longer exists", postId);
                return;
            }
            if (post.getStatus() != PostStatus.PENDING_REVIEW) {
                log.debug("Moderation skipped: post {} is {}", postId, post.getStatus());
                return;
            }

            List<String> imageUrls = post.getImages().stream()
                    .map(image -> storageService.publicUrl(image.getUrl()))
                    .toList();

            ModerationService.ModerationResult result = moderationService.review(
                    new ModerationService.PostModerationContext(
                            post.getId(),
                            post.getTitle(),
                            post.getDescription(),
                            imageUrls));

            int attempts = (post.getModerationAttempts() == null
                    ? 0
                    : post.getModerationAttempts()) + 1;

            switch (result.outcome()) {
                case APPROVED -> {
                    if (isEffectivelyBlocked(post.getUser())) {
                        post.setStatus(PostStatus.HIDDEN);
                        post.setHiddenReason("USER_BLOCKED");
                    } else {
                        post.setStatus(PostStatus.APPROVED);
                        post.setHiddenReason(null);
                    }
                    post.setRejectionReason(null);
                }
                case REJECTED -> {
                    post.setStatus(PostStatus.REJECTED);
                    post.setRejectionReason(rejectionMessages.forCode(result.code()));
                }
                case RETRY_LATER -> {
                    // Deliberately stays PENDING_REVIEW.
                    if (attempts >= properties.getMaxAttempts()) {
                        log.warn("Post {} reached max moderation attempts ({}); "
                                        + "left PENDING_REVIEW",
                                post.getId(), attempts);
                    }
                }
            }

            post.setModerationAttempts(attempts);
            post.setAiReviewResult(aiReviewResult(result, attempts));

            postRepository.save(post);

            log.info("Moderation post {} -> {} (attempt {})",
                    post.getId(), result.outcome(), attempts);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    // Fixed schema only: no free-form model text ever reaches the DB.
    private Map<String, Object> aiReviewResult(
            ModerationService.ModerationResult result, int attempts) {

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("model", properties.getModel());
        map.put("promptVersion", OpenRouterModerationService.PROMPT_VERSION);
        map.put("verdict", result.outcome().name());
        map.put("code", result.code());
        map.put("attempts", attempts);
        map.put("reviewedAt", Instant.now().toString());
        return map;
    }
    // An expired TEMP block counts as active, matching JWTfilter and login.
    private boolean isEffectivelyBlocked(User user) {
        if (user == null) {
            return false;
        }
        if (user.getStatus() == UserStatus.PERM_BLOCKED) {
            return true;
        }
        if (user.getStatus() == UserStatus.TEMP_BLOCKED) {
            return user.getBlockedUntil() != null
                    && user.getBlockedUntil().isAfter(Instant.now());
        }
        return false;
    }
}