package com.eexchange.backend.Audit;

import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * A single audit event stored in PostgreSQL ({@code admin_audit_log}).
 * <p>
 * {@code actorName} is a snapshot taken at write time so historical entries
 * stay readable even if the admin account is later renamed or deleted.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private AuditActorType actorType;

    /** Null for SYSTEM events. */
    @Column(name = "actor_id")
    private Long actorId;

    /** Admin username snapshot, or "system". */
    @Column(name = "actor_name", nullable = false, length = 100)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata;

    /** Correlation id from the request MDC, when present. */
    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
