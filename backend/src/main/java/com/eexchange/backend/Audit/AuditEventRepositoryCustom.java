package com.eexchange.backend.Audit;

import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/**
 * Dynamic audit queries. Implemented with the JPA Criteria API because the
 * filters are all optional and a derived query method would need a
 * combinatorial number of signatures.
 */
public interface AuditEventRepositoryCustom {

    Page<AuditEvent> search(AuditTargetType targetType,
                            Long targetId,
                            Long actorId,
                            AuditAction action,
                            Instant from,
                            Instant to,
                            Pageable pageable);

    List<AuditEvent> findRecentByTarget(AuditTargetType targetType,
                                        Long targetId,
                                        int limit);
}
