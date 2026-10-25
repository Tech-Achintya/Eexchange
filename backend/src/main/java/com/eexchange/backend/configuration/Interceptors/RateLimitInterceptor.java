package com.eexchange.backend.configuration.Interceptors;

import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Exception.RateLimitExceededException;
import com.eexchange.backend.configuration.CustomAnnotations.RateLimit;
import com.eexchange.backend.configuration.RedisRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisRateLimiter rateLimiter;

    public RateLimitInterceptor(RedisRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String name = rateLimit.name().isBlank()
                ? handlerMethod.getBeanType().getSimpleName()
                + "." + handlerMethod.getMethod().getName()
                : rateLimit.name();

        String identifier = resolveIdentifier(request, rateLimit.scope());
        String key = "ratelimit:" + name + ":" + identifier;

        RedisRateLimiter.RateLimitResult result =
                rateLimiter.consume(key, rateLimit.limit(), rateLimit.windowSeconds());

        if (!result.allowed()) {
            throw new RateLimitExceededException(
                    "Action rate limit reached. Please try again shortly.",
                    result.retryAfterSeconds());
        }

        return true;
    }

    private String resolveIdentifier(HttpServletRequest request, RateLimit.Scope scope) {
        if (scope == RateLimit.Scope.IP) {
            return "ip:" + request.getRemoteAddr();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object principal = authentication.getPrincipal();

            if (principal instanceof UserPrincipal userPrincipal) {
                return "user:" + userPrincipal.userId();
            }
            if (principal instanceof AdminPrincipal adminPrincipal) {
                return "admin:" + adminPrincipal.adminId();
            }
        }

        // scope USER but unauthenticated: fall back to IP.
        return "ip:" + request.getRemoteAddr();
    }
}