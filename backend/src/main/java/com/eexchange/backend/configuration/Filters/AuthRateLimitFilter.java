package com.eexchange.backend.configuration.Filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.auth-rate-limit-per-minute:20}")
    private int authLimitPerMinute;

    public AuthRateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/auth/") && !path.startsWith("/auth/") && !path.startsWith("/admin/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = extractClientIp(request);
        String rateKey = "ratelimit:auth:ip:" + clientIp;

        Long requests = redisTemplate.opsForValue().increment(rateKey);
        if (requests != null && requests == 1) {
            redisTemplate.expire(rateKey, Duration.ofMinutes(1));
        }

        if (requests != null && requests > authLimitPerMinute) {
            response.setStatus(429); // 429 Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Please slow down.\",\"data\":null}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
