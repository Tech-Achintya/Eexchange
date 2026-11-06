package com.eexchange.backend.Audit;

/**
 * Who performed an audited action.
 * <p>
 * ADMIN  – a named administrator (actorId / actorName are populated).
 * SYSTEM – an automated process such as the temporary-block expiry job.
 */
public enum AuditActorType {
    ADMIN,
    SYSTEM
}
