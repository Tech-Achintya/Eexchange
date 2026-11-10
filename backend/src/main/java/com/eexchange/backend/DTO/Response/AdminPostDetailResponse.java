package com.eexchange.backend.DTO.Response;

import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.ReportReason;
import com.eexchange.backend.Entity.Enums.ReportStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminPostDetailResponse(
        Long id,
        String title,
        String description,
        BigDecimal price,
        ItemCondition condition,
        Long categoryId,
        String categoryName,
        PostStatus status,
        String rejectionReason,
        String hiddenReason,
        Integer moderationAttempts,
        Integer editCount,
        Long collegeId,
        String collegeName,
        Long sellerId,
        String sellerName,
        List<String> imageUrls,
        Map<String, Object> aiReviewResult,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        List<ReportSummary> reports
) {
    public record ReportSummary(
            Long id,
            ReportReason reason,
            String comment,
            Long reporterId,
            String reporterName,
            ReportStatus status,
            Instant createdAt
    ) {
    }
}
