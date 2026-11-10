package com.eexchange.backend.Service.Cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollegeFeedVersionService {

    private static final String KEY_PREFIX = "feed:version:college:";
    private final StringRedisTemplate redisTemplate;

    public long getVersion(Long collegeId) {
        if (collegeId == null) {
            return 1L;
        }
        try {
            String val = redisTemplate.opsForValue().get(KEY_PREFIX + collegeId);
            return (val != null) ? Long.parseLong(val) : 1L;
        } catch (Exception ex) {
            log.warn("Failed to get college feed version from Redis for college {}: {}",
                    collegeId, ex.getMessage());
            return 1L;
        }
    }

    /**
     * Invalidates every cached feed permutation for a college.
     * <p>
     * When called inside a transaction the increment runs after commit. This is required:
     * bumping the version before commit lets a concurrent request repopulate the new
     * version from the pre-commit snapshot, leaving stale data cached for the full TTL.
     */
    public void incrementVersion(Long collegeId) {
        if (collegeId == null) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            doIncrement(collegeId);
                        }
                    });
            return;
        }

        doIncrement(collegeId);
    }

    private void doIncrement(Long collegeId) {
        try {
            redisTemplate.opsForValue().increment(KEY_PREFIX + collegeId);
            log.debug("Incremented feed cache version for college {}", collegeId);
        } catch (Exception ex) {
            log.warn("Failed to increment college feed version in Redis for college {}: {}",
                    collegeId, ex.getMessage());
        }
    }
}
