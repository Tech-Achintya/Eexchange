package com.eexchange.backend.Service;

import com.eexchange.backend.Service.Moderation.ModerationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

// Allowed only under the dev profile; ModerationPropertiesValidator enforces that.
@Service
@ConditionalOnProperty(name = "moderation.provider", havingValue = "stub")
public class StubModerationService implements ModerationService {

    @Override
    public ModerationResult review(PostModerationContext context) {
        return ModerationResult.approved();
    }
}