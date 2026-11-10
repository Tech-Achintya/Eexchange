package com.eexchange.backend.Audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one audit event in its own transaction.
 * <p>
 * The writer is deliberately a separate bean: the audit event is produced in
 * {@code afterCommit} of the caller's transaction, where joining the completed
 * transaction would be unsafe. {@code REQUIRES_NEW} suspends it and commits
 * the audit row independently. Audit failures never roll back business work.
 */
@Component
@RequiredArgsConstructor
public class AuditEventWriter {

    private final AuditEventRepository auditEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditEvent event) {
        auditEventRepository.save(event);
    }
}
