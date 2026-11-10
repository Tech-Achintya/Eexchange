package com.eexchange.backend.Audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository
        extends JpaRepository<AuditEvent, Long>, AuditEventRepositoryCustom {
}
