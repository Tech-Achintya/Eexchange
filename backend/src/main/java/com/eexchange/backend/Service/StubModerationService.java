package com.eexchange.backend.Service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "app.moderation.provider",
        havingValue = "stub",
        matchIfMissing = true)
public class StubModerationService implements ModerationService {

    @Override
    public ModerationResult review(PostModerationContext context) {
        return new ModerationResult(true, null);
    }
}