package com.eexchange.backend.configuration.Filters;

import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.AdminRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Service.CustomUserDetailsService;
import com.eexchange.backend.utils.JWTutil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class JWTfilter extends OncePerRequestFilter {

    private final JWTutil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    public JWTfilter(JWTutil jwtUtil,
                     CustomUserDetailsService userDetailsService,
                     StringRedisTemplate redisTemplate,
                     UserRepository userRepository,
                     AdminRepository adminRepository) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/") || path.startsWith("/api/admin/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String path = request.getServletPath();
        final boolean isAdminRoute = isAdminPath(path);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            boolean authenticated = isAdminRoute
                    ? authenticateAdmin(token, request, response)
                    : authenticateUser(token, request, response);

            if (!authenticated) {
                return;
            }
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired JWT token.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAdminPath(String path) {
        return path.equals("/api/admin") || path.startsWith("/api/admin/");
    }

    private boolean authenticateAdmin(String token,
                                      HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {

        Long adminId = jwtUtil.extractAdminId(token);
        if (adminId == null) {
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid admin authentication token.");
            return false;
        }

        Admin admin = adminRepository.findById(adminId).orElse(null);
        if (admin == null || !Boolean.TRUE.equals(admin.getIsActive())) {
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Admin account is inactive or no longer exists.");
            return false;
        }

        AdminPrincipal principal = new AdminPrincipal(
                admin.getId(), admin.getUsername(), admin.getRole());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + admin.getRole().name()))
                );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    private boolean authenticateUser(String token,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {

        String username = jwtUtil.extractUsername(token);
        Long userId = jwtUtil.extractUserId(token);

        if (userId == null || username == null) {
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid user authentication token.");
            return false;
        }

        String statusKey = "user:status:" + userId;
        String status = redisTemplate.opsForValue().get(statusKey);

        if (status == null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "User account no longer exists.");
                return false;
            }
            status = user.getStatus().name();
            redisTemplate.opsForValue().set(statusKey, status, Duration.ofHours(1));
        }

        if (UserStatus.PERM_BLOCKED.name().equals(status)) {
            sendJsonError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Your account has been permanently suspended.");
            return false;
        }

        if (UserStatus.TEMP_BLOCKED.name().equals(status)) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getBlockedUntil() != null
                    && user.getBlockedUntil().isAfter(Instant.now())) {
                sendJsonError(response, HttpServletResponse.SC_FORBIDDEN,
                        "Account temporarily suspended until: " + user.getBlockedUntil());
                return false;
            }
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (!jwtUtil.validateToken(token, userDetails)) {
                sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "Invalid or expired JWT token.");
                return false;
            }

            UserPrincipal principal = new UserPrincipal(
                    userId, username, jwtUtil.extractCollegeId(token));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        return true;
    }

    private void sendJsonError(HttpServletResponse response,
                               int status,
                               String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                String.format("{\"success\":false,\"message\":\"%s\",\"data\":null}", message));
    }
}