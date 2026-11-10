package com.eexchange.backend.Service;

import com.eexchange.backend.Audit.AuditActorType;
import com.eexchange.backend.Audit.AuditEvent;
import com.eexchange.backend.Audit.AuditEventWriter;
import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Repository.AdminRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AuditServiceTest {

    private AuditEventWriter auditEventWriter;
    private AdminRepository adminRepository;
    private AuditService service;

    @BeforeEach
    void setUp() {
        auditEventWriter = mock(AuditEventWriter.class);
        adminRepository = mock(AdminRepository.class);
        service = new AuditService(auditEventWriter, adminRepository,
                new ObjectMapper().findAndRegisterModules());

        when(adminRepository.getReferenceById(1L))
                .thenReturn(Admin.builder().id(1L).username("root").build());
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void writesOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.log(1L, AuditAction.BLOCK_TEMP, AuditTargetType.USER, 20L,
                "Repeated spam", Map.of("postsHidden", 2));

        verifyNoInteractions(auditEventWriter);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        AuditEvent saved = captureSingleSave();
        assertThat(saved.getAction()).isEqualTo(AuditAction.BLOCK_TEMP);
        assertThat(saved.getTargetId()).isEqualTo(20L);
        assertThat(saved.getMetadata()).containsEntry("postsHidden", 2);
    }

    @Test
    void writesNothingOnRollback() {
        TransactionSynchronizationManager.initSynchronization();

        service.log(1L, AuditAction.UNBLOCK, AuditTargetType.USER, 20L,
                "Appeal accepted", Map.of());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(auditEventWriter, never()).write(any());
    }

    @Test
    void swallowsRepositoryFailureAndLogsRecoverableEvent(CapturedOutput output) {
        doThrow(new RuntimeException("database unavailable"))
                .when(auditEventWriter).write(any(AuditEvent.class));

        assertThatCode(() -> service.log(1L, AuditAction.DELETE_POST,
                AuditTargetType.POST, 77L, "Policy violation", Map.of()))
                .doesNotThrowAnyException();

        assertThat(output)
                .contains("AUDIT_WRITE_FAILED")
                .contains("DELETE_POST")
                .contains("77")
                .contains("database unavailable");
    }

    @Test
    void buildsAdminAndSystemDocuments() {
        service.log(1L, AuditAction.BLOCK_PERM, AuditTargetType.USER, 20L,
                "Fraud", Map.of());

        service.logSystem(AuditAction.AUTO_UNBLOCK, AuditTargetType.USER, 20L,
                "Temporary block expired", Map.of("postsRestored", 2));

        ArgumentCaptor<AuditEvent> captor =
                ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventWriter, times(2)).write(captor.capture());

        List<AuditEvent> saved = captor.getAllValues();
        assertThat(saved.get(0).getActorType()).isEqualTo(AuditActorType.ADMIN);
        assertThat(saved.get(0).getActorId()).isEqualTo(1L);
        assertThat(saved.get(0).getActorName()).isEqualTo("root");

        assertThat(saved.get(1).getActorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(saved.get(1).getActorId()).isNull();
        assertThat(saved.get(1).getActorName()).isEqualTo("system");
        assertThat(saved.get(1).getAction()).isEqualTo(AuditAction.AUTO_UNBLOCK);
    }

    @Test
    void capturesRequestIdFromMdc() {
        MDC.put("requestId", "req-123");

        service.log(1L, AuditAction.BLOCK_TEMP, AuditTargetType.USER, 20L,
                "Spam", Map.of());

        assertThat(captureSingleSave().getRequestId()).isEqualTo("req-123");
    }

    @Test
    void sanitizesMetadataKeysAndValues() {
        Instant when = Instant.parse("2026-01-02T03:04:05Z");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("a.b", "dotted");
        metadata.put("$in", "injection");
        metadata.put("action", AuditAction.BLOCK_TEMP);
        metadata.put("at", when);
        metadata.put("target", AuditTargetType.USER);
        metadata.put("count", 5);
        metadata.put("flag", true);
        metadata.put("missing", null);

        service.log(1L, AuditAction.BLOCK_TEMP, AuditTargetType.USER, 20L,
                "Spam", metadata);

        Map<String, Object> saved = captureSingleSave().getMetadata();
        assertThat(saved).containsKeys("a_b", "_in", "action", "at", "target",
                "count", "flag", "missing");
        assertThat(saved.get("action")).isEqualTo("BLOCK_TEMP");
        assertThat(saved.get("at")).isEqualTo(when.toString());
        assertThat(saved.get("target")).isEqualTo("USER");
        assertThat(saved.get("count")).isEqualTo(5);
        assertThat(saved.get("flag")).isEqualTo(true);
        assertThat(saved.get("missing")).isNull();
    }

    private AuditEvent captureSingleSave() {
        ArgumentCaptor<AuditEvent> captor =
                ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventWriter).write(captor.capture());
        return captor.getValue();
    }
}
