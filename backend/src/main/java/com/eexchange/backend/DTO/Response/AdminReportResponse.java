package com.eexchange.backend.DTO.Response;

import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.ReportReason;

import java.time.Instant;

public record AdminReportResponse(
        Long id,
        ReportReason reason,
        String comment,
        Long reporterId,
        String reporterName,
        Long postId,
        String postTitle,
        PostStatus postStatus,
        long openReportCount,
        Instant createdAt
) {
}