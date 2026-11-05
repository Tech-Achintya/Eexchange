package com.eexchange.backend.DTO.Response;

import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.ListingType;
import com.eexchange.backend.Entity.Enums.PostStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Full post. Also used by /api/posts/mine so the owner sees status + reason.
public record PostDetailResponse(
        Long id,
        String title,
        String description,
        BigDecimal price,
        ListingType listingType,
        String exchangeWanted,
        ItemCondition condition,
        Long categoryId,
        String categoryName,
        PostStatus status,
        String rejectionReason,
        Long collegeId,
        Long sellerId,
        String sellerName,
        List<String> imageUrls,
        Instant createdAt,
        Instant expiresAt,
        boolean interested
) {
}