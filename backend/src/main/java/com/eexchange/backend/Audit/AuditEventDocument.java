package com.eexchange.backend.Audit;

import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * A single audit event stored in MongoDB.
 * <p>
 * {@code actorName} is a snapshot taken at write time so historical entries
 * stay readable even if the admin account is later renamed or deleted.
 */
@Document(collection = "audit_events")
@CompoundIndexes({
        @CompoundIndex(name = "target_created_idx",
                def = "{'targetType': 1, 'targetId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "actor_created_idx",
                def = "{'actorId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "created_idx",
                def = "{'createdAt': -1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventDocument {

    @Id
    private String id;

    private AuditActorType actorType;

    /** Null for SYSTEM events. */
    private Long actorId;

    /** Admin username snapshot, or "system". */
    private String actorName;

    private AuditAction action;

    private AuditTargetType targetType;

    private Long targetId;

    private String reason;

    private Map<String, Object> metadata;

    /** Correlation id from the request MDC, when present. */
    private String requestId;

    private Instant createdAt;
}
