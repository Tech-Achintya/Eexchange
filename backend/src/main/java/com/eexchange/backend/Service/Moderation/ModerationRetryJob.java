package com.eexchange.backend.Service.Moderation;

import com.eexchange.backend.Configuration.ModerationProperties;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationRetryJob {

    private final PostRepository postRepository;
    private final PostModerationProcessor processor;
    private final ModerationBudget budget;
    private final ModerationProperties properties;

    @Scheduled(fixedDelayString = "${moderation.job-delay-ms:60000}")
    public void run() {
        if (budget.isExhausted()) {
            log.info("Moderation retry job skipped: daily budget exhausted");
            return;
        }

        Instant now = Instant.now();

        List<Post> candidates = postRepository.findPendingForModeration(
                PostStatus.PENDING_REVIEW,
                properties.getMaxAttempts(),
                now,
                PageRequest.of(0, properties.getJobBatchSize()));

        for (Post post : candidates) {
            if (budget.isExhausted()) {
                log.info("Moderation retry job stopping: daily budget exhausted");
                return;
            }

            // Manual review is a human queue, never an AI retry candidate.
            if (post.getStatus() != PostStatus.PENDING_REVIEW) {
                continue;
            }

            int attempts = post.getModerationAttempts() == null
                    ? 0
                    : post.getModerationAttempts();

            // N failed attempts wait backoff[N-1]; the last value is reused.
            Instant dueAt = post.getUpdatedAt()
                    .plus(Duration.ofMinutes(backoffMinutes(attempts)));

            if (dueAt.isAfter(now)) {
                continue;
            }

            processor.process(post.getId());
        }
    }

    private long backoffMinutes(int attempts) {
        List<Integer> backoff = properties.getBackoffMinutes();

        if (attempts <= 0 || backoff == null || backoff.isEmpty()) {
            return 0L;
        }

        int index = Math.min(attempts - 1, backoff.size() - 1);
        Integer minutes = backoff.get(index);
        return minutes == null || minutes < 0 ? 0L : minutes;
    }
}