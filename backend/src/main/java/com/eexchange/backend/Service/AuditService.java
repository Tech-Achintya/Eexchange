package com.eexchange.backend.Service;

import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.AdminAuditLog;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Repository.AdminAuditLogRepository;
import com.eexchange.backend.Repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AdminRepository adminRepository;

    // MANDATORY: an audit row can never be written outside the caller's
    // business transaction, so an audit failure rolls the business change back.
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(Long adminId,
                    AuditAction action,
                    AuditTargetType targetType,
                    Long targetId,
                    String reason,
                    Map<String, Object> metadata) {

        Admin admin = adminRepository.getReferenceById(adminId);

        adminAuditLogRepository.save(AdminAuditLog.builder()
                .admin(admin)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .metadata(metadata)
                .build());
    }
}