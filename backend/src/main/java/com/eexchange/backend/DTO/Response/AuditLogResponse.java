package com.eexchange.backend.DTO.Response;

import com.eexchange.backend.Audit.AuditActorType;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        String id,
        AuditActorType actorType,
        Long actorId,
        String actorName,
        AuditAction action,
        AuditTargetType targetType,
        Long targetId,
        String reason,
        Map<String, Object> metadata,
        Instant createdAt
) {
}
