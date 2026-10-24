package com.eexchange.backend.configuration.Filters;

import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Service.CustomUserDetailsService;
import com.eexchange.backend.utils.JWTutil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Component
public class JWTfilter extends OncePerRequestFilter {

    private final JWTutil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    public JWTfilter(JWTutil jwtUtil,
                     CustomUserDetailsService userDetailsService,
                     StringRedisTemplate redisTemplate,
                     UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/") || path.startsWith("/auth/") || path.startsWith("/admin/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String path = request.getServletPath();
        final boolean isAdminRoute = path.startsWith("/admin/");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            // ==========================================
            // ADMIN ROUTE VALIDATION
            // ==========================================
            if (isAdminRoute) {
                Claims claims = jwtUtil.extractAdminClaims(token);
                String role = (String) claims.get("role");

                if (!"ROLE_ADMIN".equals(role) && !"ROLE_SUPER_ADMIN".equals(role)) {
                    sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied: Admin authority required.");
                    return;
                }

                UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(),
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                );
                adminAuth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(adminAuth);
                filterChain.doFilter(request, response);
                return;
            }

            // ==========================================
            // USER ROUTE VALIDATION
            // ==========================================
            String username = jwtUtil.extractUsername(token);
            Long userId = jwtUtil.extractUserId(token);

            if (userId == null || username == null) {
                sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid user authentication token.");
                return;
            }

            // 1. Check Redis user status cache (Fast path)
            String statusKey = "user:status:" + userId;
            String status = redisTemplate.opsForValue().get(statusKey);

            // 2. Cache miss -> Database fallback
            if (status == null) {
                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "User account no longer exists.");
                    return;
                }
                status = user.getStatus().name();
                redisTemplate.opsForValue().set(statusKey, status, Duration.ofHours(1));
            }

            // 3. Status enforcement
            if (UserStatus.PERM_BLOCKED.name().equals(status)) {
                sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Your account has been permanently suspended.");
                return;
            }

            if (UserStatus.TEMP_BLOCKED.name().equals(status)) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null && user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(Instant.now())) {
                    sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Account temporarily suspended until: " + user.getBlockedUntil());
                    return;
                }
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                if (jwtUtil.validateToken(token, userDetails)) {
                    // Attach UserPrincipal for @CurrentUser resolution
                    Long collegeId = jwtUtil.extractCollegeId(token);
                    UserPrincipal principal = new UserPrincipal(userId, username, collegeId);

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            principal, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        } catch (Exception e) {
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT token.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format("{\"success\":false,\"message\":\"%s\",\"data\":null}", message));
    }
}
