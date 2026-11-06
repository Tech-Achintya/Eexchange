package com.eexchange.backend.Service;

import com.eexchange.backend.Audit.AuditEventDocument;
import com.eexchange.backend.Audit.AuditEventRepository;
import com.eexchange.backend.DTO.Response.AuditLogResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AuditEventRepository auditEventRepository;

    public PageResponse<AuditLogResponse> list(AuditTargetType targetType,
                                               Long targetId,
                                               Long actorId,
                                               AuditAction action,
                                               Instant from,
                                               Instant to,
                                               int page,
                                               int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<AuditEventDocument> logs = auditEventRepository.search(
                targetType, targetId, actorId, action, from, to, pageable);

        List<AuditLogResponse> content = logs.getContent().stream()
                .map(AdminAuditService::toResponse)
                .toList();

        return new PageResponse<>(content, logs.getNumber(), logs.getSize(),
                logs.getTotalElements(), logs.getTotalPages());
    }

    static AuditLogResponse toResponse(AuditEventDocument event) {
        return new AuditLogResponse(
                event.getId(),
                event.getActorType(),
                event.getActorId(),
                event.getActorName(),
                event.getAction(),
                event.getTargetType(),
                event.getTargetId(),
                event.getReason(),
                event.getMetadata(),
                event.getCreatedAt());
    }
}
