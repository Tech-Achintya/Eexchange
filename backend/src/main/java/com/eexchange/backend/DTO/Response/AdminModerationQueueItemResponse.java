package com.eexchange.backend.DTO.Response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminModerationQueueItemResponse(
        Long id,
        String title,
        BigDecimal price,
        Long sellerId,
        String sellerName,
        Long collegeId,
        String collegeName,
        Instant createdAt,
        Instant queuedAt,
        String lastFailure,
        Integer attempts,
        List<String> imageUrls
) {
}
