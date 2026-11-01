package com.eexchange.backend.Service;

import java.util.List;

public interface ModerationService {

    ModerationResult review(PostModerationContext context);

    record PostModerationContext(
            Long postId,
            String title,
            String description,
            List<String> imageUrls
    ) {
    }
    record ModerationResult(
            boolean approved,
            String rejectionReason
    ) {
    }
}