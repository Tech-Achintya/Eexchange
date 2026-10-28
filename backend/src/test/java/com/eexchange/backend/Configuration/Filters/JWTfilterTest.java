package com.eexchange.backend.Configuration.Filters;

import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.AdminRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Service.CustomUserDetailsService;
import com.eexchange.backend.Utils.JWTutil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JWTfilterTest {

    private JWTutil jwtUtil;
    private CustomUserDetailsService userDetailsService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private UserRepository userRepository;
    private AdminRepository adminRepository;
    private JWTfilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwtUtil = mock(JWTutil.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        userRepository = mock(UserRepository.class);
        adminRepository = mock(AdminRepository.class);

        filter = new JWTfilter(
                jwtUtil, userDetailsService, redisTemplate, userRepository, adminRepository);

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userTokenOnAdminRouteReturns401() throws Exception {
        when(jwtUtil.extractAdminId("user-token"))
                .thenThrow(new JwtException("wrong signing key"));

        MockHttpServletResponse response = run("/api/admin/ping", "user-token");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void adminTokenOnUserRouteReturns401() throws Exception {
        when(jwtUtil.extractUsername("admin-token"))
                .thenThrow(new JwtException("wrong signing key"));

        MockHttpServletResponse response = run("/api/saved-posts", "admin-token");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void permanentlyBlockedUserReturns403() throws Exception {
        when(jwtUtil.extractUsername("token")).thenReturn("blocked@college.edu");
        when(jwtUtil.extractUserId("token")).thenReturn(7L);
        when(valueOps.get("user:status:7")).thenReturn("PERM_BLOCKED");

        MockHttpServletResponse response = run("/api/saved-posts", "token");

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void temporarilyBlockedUserWithFutureBlockedUntilReturns403() throws Exception {
        when(jwtUtil.extractUsername("token")).thenReturn("temp@college.edu");
        when(jwtUtil.extractUserId("token")).thenReturn(8L);
        when(valueOps.get("user:status:8")).thenReturn("TEMP_BLOCKED");

        User user = User.builder()
                .id(8L)
                .blockedUntil(Instant.now().plusSeconds(3600))
                .build();
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));

        MockHttpServletResponse response = run("/api/saved-posts", "token");

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void deletedUserReturns401() throws Exception {
        when(jwtUtil.extractUsername("token")).thenReturn("deleted@college.edu");
        when(jwtUtil.extractUserId("token")).thenReturn(9L);
        when(valueOps.get("user:status:9")).thenReturn("ACTIVE");
        when(userDetailsService.loadUserByUsername("deleted@college.edu"))
                .thenThrow(new UsernameNotFoundException("gone"));

        MockHttpServletResponse response = run("/api/saved-posts", "token");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void activeUserGetsUserPrincipal() throws Exception {
        when(jwtUtil.extractUsername("token")).thenReturn("user@college.edu");
        when(jwtUtil.extractUserId("token")).thenReturn(5L);
        when(jwtUtil.extractCollegeId("token")).thenReturn(3L);
        when(valueOps.get("user:status:5")).thenReturn("ACTIVE");

        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername("user@college.edu")
                .password("x")
                .authorities("ROLE_USER")
                .build();
        when(userDetailsService.loadUserByUsername("user@college.edu")).thenReturn(details);
        when(jwtUtil.validateToken("token", details)).thenReturn(true);

        MockHttpServletResponse response = run("/api/saved-posts", "token");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new UserPrincipal(5L, "user@college.edu", 3L));
    }

    @Test
    void activeAdminGetsAdminPrincipalWithRealRole() throws Exception {
        when(jwtUtil.extractAdminId("admin-token")).thenReturn(1L);

        Admin admin = Admin.builder()
                .id(1L)
                .username("moderator")
                .email("moderator@example.com")
                .passwordHash("x")
                .role(AdminRole.MODERATOR)
                .isActive(true)
                .build();
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        MockHttpServletResponse response = run("/api/admin/ping", "admin-token");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new AdminPrincipal(1L, "moderator", AdminRole.MODERATOR));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_MODERATOR");
    }

    @Test
    void inactiveAdminReturns401() throws Exception {
        when(jwtUtil.extractAdminId("admin-token")).thenReturn(1L);

        Admin admin = Admin.builder()
                .id(1L)
                .username("admin")
                .email("admin@example.com")
                .passwordHash("x")
                .role(AdminRole.SUPER_ADMIN)
                .isActive(false)
                .build();
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        MockHttpServletResponse response = run("/api/admin/ping", "admin-token");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void authRouteIsSkippedEvenWithStaleBearerToken() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.addHeader("Authorization", "Bearer stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtUtil, userDetailsService, redisTemplate, userRepository, adminRepository);
    }

    private MockHttpServletResponse run(String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.addHeader("Authorization", "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
