package com.eexchange.backend.Service;

import com.eexchange.backend.Configuration.AdminArgumentResolver;
import com.eexchange.backend.Controller.AdminAuditController;
import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Exception.GlobalExceptionHandler;
import com.eexchange.backend.Service.AdminAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSecurityMockMvcTest {

    private AdminAuditService adminAuditService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminAuditService = mock(AdminAuditService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminAuditController(adminAuditService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AdminArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void moderatorGets403OnAuditLog() throws Exception {
        authenticate(new AdminPrincipal(2L, "mod", AdminRole.MODERATOR));

        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminAuditService);
    }

    @Test
    void superAdminGets200OnAuditLog() throws Exception {
        authenticate(new AdminPrincipal(1L, "root", AdminRole.SUPER_ADMIN));
        when(adminAuditService.list(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                any(Integer.class), any(Integer.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminFiltersAreForwarded() throws Exception {
        authenticate(new AdminPrincipal(1L, "root", AdminRole.SUPER_ADMIN));
        when(adminAuditService.list(
                any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/admin/audit-log")
                        .param("targetType", "USER")
                        .param("targetId", "20")
                        .param("actorId", "1")
                        .param("action", "AUTO_UNBLOCK")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-02-01T00:00:00Z")
                        .param("page", "1")
                        .param("size", "50"))
                .andExpect(status().isOk());

        verify(adminAuditService).list(
                eq(AuditTargetType.USER), eq(20L), eq(1L), eq(AuditAction.AUTO_UNBLOCK),
                eq(Instant.parse("2026-01-01T00:00:00Z")),
                eq(Instant.parse("2026-02-01T00:00:00Z")),
                eq(1), eq(50));
    }

    @Test
    void noAuthenticationGets401() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminAuditService);
    }

    @Test
    void userPrincipalGets401OnAdminRoute() throws Exception {
        // Mirrors JWTfilter: a user token on /api/admin/** never yields an AdminPrincipal.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new com.eexchange.backend.DTO.UserPrincipal(7L, "u@c.edu", 10L),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminAuditService);
    }

    private void authenticate(AdminPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))));
    }
}