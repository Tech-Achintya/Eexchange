package com.eexchange.backend.configuration.Filters;

import com.eexchange.backend.DTO.ApiResponse;
import com.eexchange.backend.configuration.RedisRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final int authLimitPerMinute;

    public AuthRateLimitFilter(RedisRateLimiter rateLimiter,
                               ObjectMapper objectMapper,
                               @Value("${app.security.auth-rate-limit-per-minute:20}")
                               int authLimitPerMinute) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.authLimitPerMinute = authLimitPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getServletPath();
        return !path.startsWith("/api/auth/") && !path.startsWith("/api/admin/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // With server.forward-headers-strategy=native, Tomcat rewrites remoteAddr
        // from X-Forwarded-For only for trusted proxies. Direct clients cannot spoof it.
        String key = "ratelimit:auth:ip:" + request.getRemoteAddr();

        RedisRateLimiter.RateLimitResult result =
                rateLimiter.consume(key, authLimitPerMinute, 60);

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER,
                    String.valueOf(result.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error("Too many requests. Please slow down."));
            return;
        }

        filterChain.doFilter(request, response);
    }
}