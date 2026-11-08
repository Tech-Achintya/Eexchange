package com.eexchange.backend.Exception;

/**
 * Raised by the rate-limit interceptor when Redis fails. The original
 * exception is kept as the cause so the failure is never swallowed.
 */
public class RedisUnavailableException extends RuntimeException {

    public RedisUnavailableException(Throwable cause) {
        super(RedisFailureSupport.UNAVAILABLE_MESSAGE, cause);
    }
}
