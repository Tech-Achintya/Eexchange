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

            int attempts = post.getModerationAttempts() == null
                    ? 0
                    : post.getModerationAttempts();

            // Backoff grows with each failed attempt: attempts * backoffMinutes.
            Instant dueAt = post.getUpdatedAt()
                    .plus(Duration.ofMinutes((long) attempts * properties.getBackoffMinutes()));

            if (dueAt.isAfter(now)) {
                continue;
            }

            processor.process(post.getId());
        }
    }
}