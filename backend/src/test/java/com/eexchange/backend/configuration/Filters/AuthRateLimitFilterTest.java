package com.eexchange.backend.configuration.Filters;

import com.eexchange.backend.configuration.RedisRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRateLimitFilterTest {

    private RedisRateLimiter rateLimiter;
    private AuthRateLimitFilter filter;
    private Map<String, Integer> counts;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RedisRateLimiter.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new AuthRateLimitFilter(rateLimiter, objectMapper, 20);
        counts = new HashMap<>();

        when(rateLimiter.consume(anyString(), anyInt(), anyInt())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            int limit = invocation.getArgument(1);
            int current = counts.merge(key, 1, Integer::sum);

            return current <= limit
                    ? new RedisRateLimiter.RateLimitResult(true, 0)
                    : new RedisRateLimiter.RateLimitResult(false, 60);
        });
    }

    @Test
    void twentyFirstRequestFromOneIpReturns429() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse last = null;

        for (int i = 0; i < 21; i++) {
            last = run(chain, "10.1.2.3", null);
        }

        assertThat(last.getStatus()).isEqualTo(429);
        assertThat(last.getHeader("Retry-After")).isEqualTo("60");
        verify(chain, times(20)).doFilter(any(), any());
    }

    @Test
    void spoofedForwardedForHeaderDoesNotBypassTheLimit() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse last = null;

        for (int i = 0; i < 21; i++) {
            last = run(chain, "10.1.2.3", "203.0.113." + (i + 1));
        }

        assertThat(last.getStatus()).isEqualTo(429);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter, times(21)).consume(keyCaptor.capture(), eq(20), eq(60));
        assertThat(keyCaptor.getAllValues())
                .containsOnly("ratelimit:auth:ip:10.1.2.3");
    }

    private MockHttpServletResponse run(FilterChain chain,
                                        String remoteAddr,
                                        String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
