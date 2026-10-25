package com.eexchange.backend.configuration.Interceptors;

import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Exception.GlobalExceptionHandler;
import com.eexchange.backend.configuration.CustomAnnotations.RateLimit;
import com.eexchange.backend.configuration.RedisRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitInterceptorTest {

    private RedisRateLimiter rateLimiter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RedisRateLimiter.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RateLimitInterceptor(rateLimiter))
                .build();

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(42L, "user@college.edu", 1L),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exceedingLimitReturns429WithRetryAfter() throws Exception {
        when(rateLimiter.consume(anyString(), anyInt(), anyInt()))
                .thenReturn(new RedisRateLimiter.RateLimitResult(false, 37));

        mockMvc.perform(get("/test/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "37"))
                .andExpect(jsonPath("$.success").value(false));

        verify(rateLimiter).consume(eq("ratelimit:limited:user:42"), eq(5), eq(60));
    }

    @Test
    void underLimitPassesThrough() throws Exception {
        when(rateLimiter.consume(anyString(), anyInt(), anyInt()))
                .thenReturn(new RedisRateLimiter.RateLimitResult(true, 0));

        mockMvc.perform(get("/test/limited"))
                .andExpect(status().isOk());
    }

    @Test
    void differentRateLimitNamesDoNotShareACounter() throws Exception {
        when(rateLimiter.consume(anyString(), anyInt(), anyInt()))
                .thenReturn(new RedisRateLimiter.RateLimitResult(true, 0));

        mockMvc.perform(get("/test/alpha")).andExpect(status().isOk());
        mockMvc.perform(get("/test/beta")).andExpect(status().isOk());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter, times(2)).consume(keyCaptor.capture(), eq(5), eq(60));

        assertThat(keyCaptor.getAllValues()).containsExactlyInAnyOrder(
                "ratelimit:alpha:user:42",
                "ratelimit:beta:user:42");
    }

    @RestController
    static class TestController {

        @GetMapping("/test/limited")
        @RateLimit(name = "limited", limit = 5, windowSeconds = 60,
                scope = RateLimit.Scope.USER)
        String limited() {
            return "ok";
        }

        @GetMapping("/test/alpha")
        @RateLimit(name = "alpha", limit = 5, windowSeconds = 60,
                scope = RateLimit.Scope.USER)
        String alpha() {
            return "alpha";
        }

        @GetMapping("/test/beta")
        @RateLimit(name = "beta", limit = 5, windowSeconds = 60,
                scope = RateLimit.Scope.USER)
        String beta() {
            return "beta";
        }
    }
}
