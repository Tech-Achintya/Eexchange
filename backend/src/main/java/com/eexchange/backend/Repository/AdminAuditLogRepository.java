package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.AdminAuditLog;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    @EntityGraph(attributePaths = {"admin"})
    @Query(value = """
            select a from AdminAuditLog a
            where (:targetType is null or a.targetType = :targetType)
              and (:adminId is null or a.admin.id = :adminId)
            """,
            countQuery = """
            select count(a) from AdminAuditLog a
            where (:targetType is null or a.targetType = :targetType)
              and (:adminId is null or a.admin.id = :adminId)
            """)
    Page<AdminAuditLog> search(@Param("targetType") AuditTargetType targetType,
                               @Param("adminId") Long adminId,
                               Pageable pageable);
}