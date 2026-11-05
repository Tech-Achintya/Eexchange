package com.eexchange.backend.Service.Event;

import com.eexchange.backend.Service.Moderation.PostModerationProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PostCreatedEventListener {

    private final PostModerationProcessor processor;

    @Async("moderationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCreated(PostCreatedEvent event) {
        processor.process(event.postId());
    }
}