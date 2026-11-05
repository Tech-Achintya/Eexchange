package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Response.AuditLogResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.AdminAuditLog;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AdminAuditLogRepository adminAuditLogRepository;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(AuditTargetType targetType,
                                               Long adminId,
                                               int page,
                                               int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<AdminAuditLog> logs = adminAuditLogRepository.search(
                targetType, adminId, pageable);

        List<AuditLogResponse> content = logs.getContent().stream()
                .map(log -> new AuditLogResponse(
                        log.getId(),
                        log.getAdmin().getId(),
                        log.getAdmin().getUsername(),
                        log.getAction(),
                        log.getTargetType(),
                        log.getTargetId(),
                        log.getReason(),
                        log.getMetadata(),
                        log.getCreatedAt()))
                .toList();

        return new PageResponse<>(content, logs.getNumber(), logs.getSize(),
                logs.getTotalElements(), logs.getTotalPages());
    }
}