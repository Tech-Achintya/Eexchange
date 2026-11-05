package com.eexchange.backend.DTO.Response;

import com.eexchange.backend.Entity.Enums.PostStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminPostCardResponse(
        Long id,
        String title,
        BigDecimal price,
        PostStatus status,
        String hiddenReason,
        Long collegeId,
        String collegeName,
        Long sellerId,
        String sellerName,
        long reportCount,
        String aiVerdict,
        Instant createdAt
) {
}
