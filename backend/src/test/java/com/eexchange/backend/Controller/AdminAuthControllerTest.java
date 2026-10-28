package com.eexchange.backend.Controller;

import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Exception.GlobalExceptionHandler;
import com.eexchange.backend.Repository.AdminRepository;
import com.eexchange.backend.Utils.JWTutil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAuthControllerTest {

    private AdminRepository adminRepository;
    private JWTutil jwtUtil;
    private PasswordEncoder passwordEncoder;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminRepository = mock(AdminRepository.class);
        jwtUtil = mock(JWTutil.class);
        passwordEncoder = new BCryptPasswordEncoder(4);

        AdminAuthController controller =
                new AdminAuthController(adminRepository, passwordEncoder, jwtUtil);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginSucceedsAndTokenUsesTheAdminsRealRole() throws Exception {
        Admin admin = Admin.builder()
                .id(2L)
                .username("moderator")
                .email("moderator@example.com")
                .passwordHash(passwordEncoder.encode("secret123"))
                .role(AdminRole.MODERATOR)
                .isActive(true)
                .build();

        when(adminRepository.findByEmail("moderator@example.com"))
                .thenReturn(Optional.of(admin));
        when(jwtUtil.generateAdminToken("moderator", 2L, "ROLE_MODERATOR"))
                .thenReturn("jwt-token");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"moderator@example.com","password":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.role").value("MODERATOR"));

        verify(jwtUtil).generateAdminToken("moderator", 2L, "ROLE_MODERATOR");
        verify(adminRepository).save(admin);
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        Admin admin = Admin.builder()
                .id(1L)
                .username("admin")
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("secret123"))
                .role(AdminRole.SUPER_ADMIN)
                .isActive(true)
                .build();

        when(adminRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"admin@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid admin credentials"));

        verify(jwtUtil, never()).generateAdminToken(anyString(), anyLong(), anyString());
    }

    @Test
    void unknownAdminReturns401WithTheSameMessage() throws Exception {
        when(adminRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("nobody@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"nobody@example.com","password":"whatever"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid admin credentials"));

        verify(adminRepository, never()).save(any());
    }

    @Test
    void inactiveAdminWithCorrectPasswordReturns403() throws Exception {
        Admin admin = Admin.builder()
                .id(1L)
                .username("admin")
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("secret123"))
                .role(AdminRole.SUPER_ADMIN)
                .isActive(false)
                .build();

        when(adminRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"admin@example.com","password":"secret123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Admin account is deactivated."));
    }

    @Test
    void inactiveAdminWithWrongPasswordReturns401() throws Exception {
        Admin admin = Admin.builder()
                .id(1L)
                .username("admin")
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("secret123"))
                .role(AdminRole.SUPER_ADMIN)
                .isActive(false)
                .build();

        when(adminRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"admin@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid admin credentials"));
    }
}
