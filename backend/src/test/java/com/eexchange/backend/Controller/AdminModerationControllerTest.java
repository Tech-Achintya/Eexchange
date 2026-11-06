package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.AdminArgumentResolver;
import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Exception.GlobalExceptionHandler;
import com.eexchange.backend.Service.AdminModerationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminModerationControllerTest {

    private AdminModerationService adminModerationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminModerationService = mock(AdminModerationService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminModerationController(adminModerationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AdminArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void moderatorGets200OnQueue() throws Exception {
        authenticate(new AdminPrincipal(2L, "mod", AdminRole.MODERATOR));
        when(adminModerationService.queue(anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/admin/moderation/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void superAdminGets200OnQueue() throws Exception {
        authenticate(new AdminPrincipal(1L, "root", AdminRole.SUPER_ADMIN));
        when(adminModerationService.queue(anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/admin/moderation/queue"))
                .andExpect(status().isOk());
    }

    @Test
    void userTokenGets401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new com.eexchange.backend.DTO.UserPrincipal(7L, "u@c.edu", 10L),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(get("/api/admin/moderation/queue"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminModerationService);
    }

    @Test
    void noAuthenticationGets401() throws Exception {
        mockMvc.perform(get("/api/admin/moderation/queue"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminModerationService);
    }

    private void authenticate(AdminPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))));
    }
}
