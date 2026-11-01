package com.eexchange.backend.Service.Event;

import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Service.ModerationService;
import com.eexchange.backend.Service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostCreatedEventListener {

    private final PostRepository postRepository;
    private final ModerationService moderationService;
    private final StorageService storageService;

    @Async("moderationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPostCreated(PostCreatedEvent event) {
        Post post = postRepository.findById(event.postId()).orElse(null);

        if (post == null) {
            log.warn("Moderation skipped: post {} no longer exists", event.postId());
            return;
        }

        if (post.getStatus() != PostStatus.PENDING_REVIEW) {
            log.debug("Moderation skipped: post {} is already {}", post.getId(), post.getStatus());
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

        if (result.approved()) {
            post.setStatus(PostStatus.APPROVED);
            post.setRejectionReason(null);
        } else {
            post.setStatus(PostStatus.REJECTED);
            post.setRejectionReason(result.rejectionReason());
        }

        postRepository.save(post);
    }
}