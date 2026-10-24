package com.eexchange.backend.configuration.Interceptors;

import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.configuration.CustomAnnotations.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    public RateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String identifier;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            identifier = "user:" + principal.userId();
        } else {
            identifier = "ip:" + request.getRemoteAddr();
        }

        String key = "ratelimit:" + handlerMethod.getMethod().getName() + ":" + identifier;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(rateLimit.windowSeconds()));
        }

        if (count != null && count > rateLimit.limit()) {
            throw new AppException("Action rate limit reached. Please try again shortly.", HttpStatus.TOO_MANY_REQUESTS);
        }

        return true;
    }
}
