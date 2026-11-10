package com.eexchange.backend.Service;

import com.eexchange.backend.Audit.AuditActorType;
import com.eexchange.backend.Audit.AuditEvent;
import com.eexchange.backend.Audit.AuditEventWriter;
import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Repository.AdminRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Best-effort audit writer.
 * <p>
 * The event is written to PostgreSQL only AFTER the caller's transaction
 * commits. A failed audit write never fails the business action: the full
 * event is logged as one JSON line prefixed {@code AUDIT_WRITE_FAILED} so it
 * can be recovered from logs. On rollback nothing is written.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private static final String SYSTEM_ACTOR_NAME = "system";
    private static final String MDC_REQUEST_ID = "requestId";

    private final AuditEventWriter auditEventWriter;
    private final AdminRepository adminRepository;
    private final ObjectMapper objectMapper;

    /**
     * Audit an action performed by an administrator.
     * <p>
     * The username is resolved immediately (inside the caller's transaction),
     * so the event carries a snapshot and never touches the Admin proxy after
     * the transaction has closed.
     */
    public void log(Long adminId,
                    AuditAction action,
                    AuditTargetType targetType,
                    Long targetId,
                    String reason,
                    Map<String, Object> metadata) {

        Admin admin = adminRepository.getReferenceById(adminId);

        writeAfterCommit(AuditEvent.builder()
                .actorType(AuditActorType.ADMIN)
                .actorId(adminId)
                .actorName(admin.getUsername())
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .metadata(sanitizeMetadata(metadata))
                .requestId(MDC.get(MDC_REQUEST_ID))
                .createdAt(Instant.now())
                .build());
    }

    /**
     * Audit an action performed by the system itself (scheduled jobs).
     */
    public void logSystem(AuditAction action,
                          AuditTargetType targetType,
                          Long targetId,
                          String reason,
                          Map<String, Object> metadata) {

        writeAfterCommit(AuditEvent.builder()
                .actorType(AuditActorType.SYSTEM)
                .actorId(null)
                .actorName(SYSTEM_ACTOR_NAME)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .metadata(sanitizeMetadata(metadata))
                .requestId(MDC.get(MDC_REQUEST_ID))
                .createdAt(Instant.now())
                .build());
    }

    private void writeAfterCommit(AuditEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            write(event);
                        }
                    });
            return;
        }

        // No transaction: there is nothing to wait for.
        write(event);
    }

    private void write(AuditEvent event) {
        try {
            auditEventWriter.write(event);
        } catch (RuntimeException ex) {
            log.error("AUDIT_WRITE_FAILED {}", toJson(event), ex);
        }
    }

    private String toJson(AuditEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            // Serialization must never break the best-effort path either.
            return String.valueOf(event);
        }
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) ->
                sanitized.put(sanitizeKey(key), sanitizeValue(value)));
        return sanitized;
    }

    private String sanitizeKey(String key) {
        String safe = key == null ? "null" : key.replace(".", "_");
        if (safe.startsWith("$")) {
            safe = "_" + safe.substring(1);
        }
        return safe;
    }

    private Object sanitizeValue(Object value) {
        if (value == null || value instanceof String
                || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        // Enums, Instant, collections and any other object become strings so
        // the JSON column never has to guess a mapping.
        return value.toString();
    }
}
