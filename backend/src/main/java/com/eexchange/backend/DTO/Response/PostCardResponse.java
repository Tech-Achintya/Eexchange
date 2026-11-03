package com.eexchange.backend.DTO.Response;
import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.ListingType;

import java.math.BigDecimal;
import java.time.Instant;

public record PostCardResponse(
        Long id,
        String title,
        BigDecimal price,
        ListingType listingType,
        ItemCondition condition,
        String firstImageUrl,
        String categoryName,
        String sellerName,
        Instant createdAt
) {
}