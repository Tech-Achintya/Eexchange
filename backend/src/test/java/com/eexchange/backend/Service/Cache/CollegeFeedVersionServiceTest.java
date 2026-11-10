package com.eexchange.backend.Service.Cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollegeFeedVersionServiceTest {

    @AfterEach
    void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void incrementRunsAfterCommitOnly() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CollegeFeedVersionService service = new CollegeFeedVersionService(redisTemplate);

        TransactionSynchronizationManager.initSynchronization();
        service.incrementVersion(7L);

        verify(valueOperations, never()).increment("feed:version:college:7");

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(valueOperations).increment("feed:version:college:7");
    }
}
