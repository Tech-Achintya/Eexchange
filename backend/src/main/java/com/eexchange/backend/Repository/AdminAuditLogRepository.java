package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.AdminAuditLog;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    Page<AdminAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(AuditTargetType targetType, Long targetId, Pageable pageable);
    Page<AdminAuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);
}
