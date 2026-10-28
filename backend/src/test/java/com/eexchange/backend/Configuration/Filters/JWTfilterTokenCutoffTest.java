package com.eexchange.backend.Configuration.Filters;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Repository.AdminRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Service.CustomUserDetailsService;
import com.eexchange.backend.Utils.JWTutil;
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

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JWTfilterTokenCutoffTest {

    private JWTutil jwtUtil;
    private CustomUserDetailsService userDetailsService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private JWTfilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwtUtil = mock(JWTutil.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        filter = new JWTfilter(
                jwtUtil,
                userDetailsService,
                redisTemplate,
                mock(UserRepository.class),
                mock(AdminRepository.class));

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tokenIssuedBeforeCutoffIsRejected() throws Exception {
        when(jwtUtil.extractUsername("old-token")).thenReturn("user@college.edu");
        when(jwtUtil.extractUserId("old-token")).thenReturn(5L);
        when(valueOperations.get("user:status:5")).thenReturn("ACTIVE");
        when(valueOperations.get("user:tokens-valid-after:5"))
                .thenReturn("1767225600000");
        when(jwtUtil.extractIssuedAt("old-token"))
                .thenReturn(new Date(1767225599000L));

        MockHttpServletResponse response = run("old-token");

        assertThat(response.getStatus()).isEqualTo(401);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void tokenIssuedAtOrAfterCutoffIsAccepted() throws Exception {
        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername("user@college.edu")
                .password("x")
                .authorities("ROLE_USER")
                .build();

        when(jwtUtil.extractUsername("new-token")).thenReturn("user@college.edu");
        when(jwtUtil.extractUserId("new-token")).thenReturn(5L);
        when(jwtUtil.extractCollegeId("new-token")).thenReturn(3L);
        when(valueOperations.get("user:status:5")).thenReturn("ACTIVE");
        when(valueOperations.get("user:tokens-valid-after:5"))
                .thenReturn("1767225600000");
        when(jwtUtil.extractIssuedAt("new-token"))
                .thenReturn(new Date(1767225600000L));
        when(userDetailsService.loadUserByUsername("user@college.edu"))
                .thenReturn(details);
        when(jwtUtil.validateToken("new-token", details)).thenReturn(true);

        MockHttpServletResponse response = run("new-token");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new UserPrincipal(5L, "user@college.edu", 3L));
    }

    @Test
    void tokenForAnonymizedAccountIsRejected() throws Exception {
        when(jwtUtil.extractUsername("old-token")).thenReturn("user@college.edu");
        when(jwtUtil.extractUserId("old-token")).thenReturn(7L);
        when(valueOperations.get("user:status:7")).thenReturn("ACTIVE");
        when(valueOperations.get("user:tokens-valid-after:7")).thenReturn(null);
        when(userDetailsService.loadUserByUsername("user@college.edu"))
                .thenThrow(new UsernameNotFoundException("User no longer exists"));

        MockHttpServletResponse response = run("old-token");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private MockHttpServletResponse run(String token) throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/saved-posts");
        request.setServletPath("/api/saved-posts");
        request.addHeader("Authorization", "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}