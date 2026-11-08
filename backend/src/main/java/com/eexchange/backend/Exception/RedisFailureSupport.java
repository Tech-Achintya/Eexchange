package com.eexchange.backend.Exception;

import com.eexchange.backend.DTO.Response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Shared fail-closed behaviour when Redis (or its cache/limiter data access)
 * is unavailable. Callers log one WARN and use these helpers for the 503.
 */
public final class RedisFailureSupport {

    public static final String UNAVAILABLE_MESSAGE =
            "Service temporarily unavailable. Please try again shortly.";
    public static final int RETRY_AFTER_SECONDS = 5;

    private RedisFailureSupport() {
    }

    public static boolean isRedisFailure(Throwable ex) {
        return ex instanceof RedisConnectionFailureException
                || ex instanceof RedisSystemException
                || ex instanceof QueryTimeoutException
                || ex instanceof DataAccessException;
    }

    public static void writeServiceUnavailable(HttpServletResponse response,
                                               ObjectMapper objectMapper) throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setHeader(HttpHeaders.RETRY_AFTER,
                String.valueOf(RETRY_AFTER_SECONDS));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(UNAVAILABLE_MESSAGE));
    }

    public static ResponseEntity<ApiResponse<Void>> serviceUnavailableResponse() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER,
                        String.valueOf(RETRY_AFTER_SECONDS))
                .body(ApiResponse.error(UNAVAILABLE_MESSAGE));
    }
}
