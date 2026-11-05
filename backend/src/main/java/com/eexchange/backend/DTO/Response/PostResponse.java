package com.eexchange.backend.DTO.Response;

import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.PostStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PostResponse(
        Long id,
        String title,
        String description,
        BigDecimal price,
        ItemCondition condition,
        Long categoryId,
        String categoryName,
        PostStatus status,
        Long collegeId,
        Long sellerId,
        String sellerName,
        List<PostImageResponse> images,
        Instant createdAt,
        Instant expiresAt
) {
    public record PostImageResponse(
            Long id,
            String url,
            Short position
    ) {
    }
}
