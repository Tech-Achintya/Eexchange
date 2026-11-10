package com.eexchange.backend.Service;

import com.eexchange.backend.Service.Cache.CollegeFeedVersionService;

import com.eexchange.backend.Configuration.AdminArgumentResolver;
import com.eexchange.backend.Controller.AdminUserController;
import com.eexchange.backend.Audit.AuditEventRepository;
import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.GlobalExceptionHandler;
import com.eexchange.backend.Repository.PostImageRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.ReportRepository;
import com.eexchange.backend.Repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserSecurityMockMvcTest {

    private UserRepository userRepository;
    private AdminUserService adminUserService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        AdminUserService realService = new AdminUserService(
                userRepository,
                mock(PostRepository.class),
                mock(PostImageRepository.class),
                mock(ReportRepository.class),
                mock(AuditEventRepository.class),
                mock(AuditService.class),
                new PostMediaCleanup(mock(StorageService.class)),
                mock(UserStatusCache.class),
                mock(EmailService.class),
                mock(CollegeFeedVersionService.class));

        ReflectionTestUtils.setField(realService, "maxTempBlockDays", 90);
        adminUserService = realService;

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminUserController(adminUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AdminArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void moderatorGets403OnPermanentBlock() throws Exception {
        authenticate(new AdminPrincipal(2L, "mod", AdminRole.MODERATOR));

        mockMvc.perform(post("/api/admin/users/20/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PERM","reason":"Fraud account"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorGets403OnPermanentUnblock() throws Exception {
        when(userRepository.findById(20L))
                .thenReturn(Optional.of(user(UserStatus.PERM_BLOCKED)));
        authenticate(new AdminPrincipal(2L, "mod", AdminRole.MODERATOR));

        mockMvc.perform(post("/api/admin/users/20/unblock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Appeal accepted"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminGets200OnTemporaryBlock() throws Exception {
        when(userRepository.findById(20L))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        authenticate(new AdminPrincipal(1L, "root", AdminRole.SUPER_ADMIN));

        mockMvc.perform(post("/api/admin/users/20/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TEMP","until":"%s","reason":"Repeated spam"}
                                """.formatted(Instant.now().plusSeconds(3600))))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminGets200OnUnblock() throws Exception {
        when(userRepository.findById(20L))
                .thenReturn(Optional.of(user(UserStatus.TEMP_BLOCKED)));
        authenticate(new AdminPrincipal(1L, "root", AdminRole.SUPER_ADMIN));

        mockMvc.perform(post("/api/admin/users/20/unblock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Appeal accepted"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void userTokenGets401OnAdminRoute() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(7L, "u@college.edu", 10L),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

    }

    private void authenticate(AdminPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))));
    }

    private User user(UserStatus status) {
        return User.builder()
                .id(20L)
                .email("user@college.edu")
                .name("Target User")
                .college(College.builder().id(10L).name("College A").build())
                .status(status)
                .build();
    }
}