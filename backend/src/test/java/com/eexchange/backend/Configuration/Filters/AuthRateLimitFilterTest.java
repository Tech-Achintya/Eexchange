package com.eexchange.backend.Configuration.Filters;

import com.eexchange.backend.Configuration.RedisRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
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
    void redisFailureFailsClosedWith503() throws Exception {
        when(rateLimiter.consume(anyString(), anyInt(), anyInt()))
                .thenThrow(new RedisConnectionFailureException("down"));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = run(chain, "10.1.2.3", null);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getContentAsString())
                .contains("Service temporarily unavailable. Please try again shortly.");
        verify(chain, times(0)).doFilter(any(), any());
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
                .containsOnly("ratelimit:auth:ip:10.1.2.3:_api_auth_login");
    }

    @Test
    void differentAuthEndpointsDoNotShareABucket() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 20; i++) {
            run(chain, "10.1.2.3", null);
        }

        MockHttpServletResponse otp = run(chain, "10.1.2.3", null,
                "/api/auth/verify-otp");

        assertThat(otp.getStatus()).isEqualTo(200);
        verify(chain, times(21)).doFilter(any(), any());
    }

    private MockHttpServletResponse run(FilterChain chain,
                                        String remoteAddr,
                                        String forwardedFor) throws Exception {
        return run(chain, remoteAddr, forwardedFor, "/api/auth/login");
    }

    private MockHttpServletResponse run(FilterChain chain,
                                        String remoteAddr,
                                        String forwardedFor,
                                        String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
