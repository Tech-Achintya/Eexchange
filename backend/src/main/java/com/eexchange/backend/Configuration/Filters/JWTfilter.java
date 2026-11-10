package com.eexchange.backend.Configuration.Filters;

import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.RedisFailureSupport;
import com.eexchange.backend.Repository.AdminRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Service.CustomUserDetailsService;
import com.eexchange.backend.Utils.JWTutil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
import java.time.Instant;
import java.util.Date;
import java.util.List;
import com.eexchange.backend.Service.TokenRevocationService;
import com.eexchange.backend.Service.UserStatusCache;


@Component
@Slf4j
public class JWTfilter extends OncePerRequestFilter {

    private final JWTutil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final ObjectMapper objectMapper;
    public JWTfilter(JWTutil jwtUtil,
                     CustomUserDetailsService userDetailsService,
                     StringRedisTemplate redisTemplate,
                     UserRepository userRepository,
                     AdminRepository adminRepository,
                     ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.objectMapper = objectMapper;
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

        String statusKey = UserStatusCache.key(userId);
        String status;
        boolean statusCacheUnavailable = false;
        try {
            status = redisTemplate.opsForValue().get(statusKey);
        } catch (RuntimeException ex) {
            if (!RedisFailureSupport.isRedisFailure(ex)) {
                throw ex;
            }
            // Redis is down: use the database as the source of truth and do
            // not write the cache back. This is the normal cache-miss path.
            statusCacheUnavailable = true;
            status = null;
            log.warn("Redis unavailable reading user status; falling back to DB ({})",
                    ex.getClass().getSimpleName());
        }

        if (status == null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "User account no longer exists.");
                return false;
            }
            status = user.getStatus().name();
            if (!statusCacheUnavailable) {
                redisTemplate.opsForValue().set(statusKey, status, UserStatusCache.TTL);
            }
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
        // Phase 2: reject user tokens issued before the last password change.
        // The cutoff is stored as epoch millis but floored to whole seconds,
        // because JWT "iat" is serialized in whole seconds too.
        // iat >= cutoff is valid. Admin tokens never reach this method.
        String cutoffValue;
        try {
            cutoffValue = redisTemplate.opsForValue()
                    .get(TokenRevocationService.KEY_TOKENS_VALID_AFTER + userId);
        } catch (RuntimeException ex) {
            if (!RedisFailureSupport.isRedisFailure(ex)) {
                throw ex;
            }
            // Can't verify the password-change cutoff: fail closed.
            log.warn("Redis unavailable reading token cutoff; failing closed ({})",
                    ex.getClass().getSimpleName());
            sendServiceUnavailable(response);
            return false;
        }

        if (cutoffValue != null) {
            long cutoffMillis;

            try {
                cutoffMillis = Long.parseLong(cutoffValue);
            } catch (NumberFormatException ex) {
                // Fail closed: a corrupt cutoff must not allow old tokens.
                sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "Invalid session. Please log in again.");
                return false;
            }

            Date issuedAt = jwtUtil.extractIssuedAt(token);
            long issuedAtMillis = issuedAt == null ? 0L : issuedAt.getTime();

            if (issuedAtMillis < cutoffMillis) {
                sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "Session expired. Please log in again.");
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

    private void sendServiceUnavailable(HttpServletResponse response) throws IOException {
        RedisFailureSupport.writeServiceUnavailable(response, objectMapper);
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