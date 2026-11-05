package com.eexchange.backend.Service;

import com.eexchange.backend.Service.Event.PostCreatedEvent;
import com.eexchange.backend.Service.Event.PostCreatedEventListener;
import com.eexchange.backend.Service.Moderation.PostModerationProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class PostCreatedEventListenerTest {

    private PostModerationProcessor processor;
    private PostCreatedEventListener listener;

    @BeforeEach
    void setUp() {
        processor = mock(PostModerationProcessor.class);
        listener = new PostCreatedEventListener(processor);
    }

    @Test
    void delegatesToTheProcessor() {
        listener.onPostCreated(new PostCreatedEvent(1L));

        verify(processor).process(1L);
    }

    @Test
    void delegatesForEveryEvent() {
        listener.onPostCreated(new PostCreatedEvent(42L));

        verify(processor).process(42L);
        verifyNoMoreInteractions(processor);
    }

    @Test
    void missingPostIsHandledByTheProcessor() {
        // The listener no longer loads the post; the processor owns that check.
        listener.onPostCreated(new PostCreatedEvent(99L));

        verify(processor).process(99L);
    }
}