package com.eexchange.backend.Service;

import com.eexchange.backend.Audit.AuditActorType;
import com.eexchange.backend.Audit.AuditEventDocument;
import com.eexchange.backend.Audit.AuditEventRepository;
import com.eexchange.backend.DTO.Response.AuditLogResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuditServiceTest {

    private AuditEventRepository auditEventRepository;
    private AdminAuditService service;

    @BeforeEach
    void setUp() {
        auditEventRepository = mock(AuditEventRepository.class);
        service = new AdminAuditService(auditEventRepository);
    }

    @Test
    void forwardsEveryFilterAndNormalizesPaging() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        AuditEventDocument event = event();

        when(auditEventRepository.search(
                eq(AuditTargetType.POST), eq(77L), eq(1L),
                eq(AuditAction.DELETE_POST), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event),
                        PageRequest.of(0, 100), 1));

        PageResponse<AuditLogResponse> result = service.list(
                AuditTargetType.POST, 77L, 1L, AuditAction.DELETE_POST,
                from, to, -1, 999);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).search(
                eq(AuditTargetType.POST), eq(77L), eq(1L),
                eq(AuditAction.DELETE_POST), eq(from), eq(to), pageable.capture());

        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")));
        assertThat(result.content()).hasSize(1);
    }

    @Test
    void defaultsToTwentyWhenSizeIsMissingAndReturnsResponseShape() {
        when(auditEventRepository.search(
                any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event()),
                        PageRequest.of(0, 20), 1));

        PageResponse<AuditLogResponse> result =
                service.list(null, null, null, null, null, null, 0, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).search(
                any(), any(), any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);

        AuditLogResponse response = result.content().get(0);
        assertThat(response.id()).isEqualTo("evt-1");
        assertThat(response.actorType()).isEqualTo(AuditActorType.ADMIN);
        assertThat(response.actorId()).isEqualTo(1L);
        assertThat(response.actorName()).isEqualTo("root");
        assertThat(response.action()).isEqualTo(AuditAction.DELETE_POST);
        assertThat(response.targetType()).isEqualTo(AuditTargetType.POST);
        assertThat(response.targetId()).isEqualTo(77L);
        assertThat(response.metadata()).containsEntry("reportId", 9);
    }

    private AuditEventDocument event() {
        return AuditEventDocument.builder()
                .id("evt-1")
                .actorType(AuditActorType.ADMIN)
                .actorId(1L)
                .actorName("root")
                .action(AuditAction.DELETE_POST)
                .targetType(AuditTargetType.POST)
                .targetId(77L)
                .reason("Policy violation")
                .metadata(Map.of("reportId", 9))
                .createdAt(Instant.parse("2026-01-15T10:00:00Z"))
                .build();
    }
}
