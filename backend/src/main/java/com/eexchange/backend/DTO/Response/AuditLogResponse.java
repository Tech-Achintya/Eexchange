package com.eexchange.backend.DTO.Response;

import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        Long adminId,
        String adminUsername,
        AuditAction action,
        AuditTargetType targetType,
        Long targetId,
        String reason,
        Map<String, Object> metadata,
        Instant createdAt
) {
}