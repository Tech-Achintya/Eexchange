package com.eexchange.backend.Configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.cache.feed")
@Getter
@Setter
public class FeedCacheProperties {

    /**
     * Default cache TTL in seconds for feed pages when no post is expiring earlier.
     */
    private long defaultTtlSeconds = 3600; // 1 hour

    /**
     * Minimum remaining TTL in seconds required for an expiring post. Pages with posts
     * expiring sooner than this are not stored.
     */
    private long minTtlSeconds = 60; // 1 minute

    /**
     * Fill lock TTL in seconds to prevent cache stampede.
     */
    private long fillLockTtlSeconds = 5; // 5 seconds
}
