package com.eexchange.backend.DTO.Response;

import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminUserDetailResponse(
        Long id,
        String email,
        String name,
        AdminUserRowResponse.CollegeSummary college,
        UserStatus status,
        Instant blockedUntil,
        String blockReason,
        Instant blockedAt,
        Boolean isCollegeVerified,
        Instant createdAt,
        Instant lastLoginAt,
        long postCount,
        Map<PostStatus, Long> postCountsByStatus,
        long openReportCount,
        List<AuditLogResponse> recentAudit
) {
}