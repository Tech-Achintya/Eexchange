package com.eexchange.backend.Service;

import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TempBlockExpiryJob {

    private static final String LOCK_KEY = "job:tempblock-expiry";
    private static final Duration LOCK_TTL = Duration.ofSeconds(55);
    private static final int BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final AdminUserService adminUserService;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 60_000)
    public void run() {
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", LOCK_TTL);

        if (!Boolean.TRUE.equals(locked)) {
            log.debug("TEMP block expiry job skipped: lock held");
            return;
        }

        try {
            Instant now = Instant.now();

            List<User> expired = userRepository.findExpiredTempBlocks(
                    UserStatus.TEMP_BLOCKED, now, PageRequest.of(0, BATCH_SIZE));

            for (User user : expired) {
                try {
                    boolean changed = adminUserService.unblockExpired(user.getId(), now);
                    if (!changed) {
                        log.debug("Skipping user {}: block state was already changed",
                                user.getId());
                    }
                } catch (RuntimeException ex) {
                    log.warn("Failed to expire TEMP block for user {}",
                            user.getId(), ex);
                }
            }
        } finally {
            redisTemplate.delete(LOCK_KEY);
        }
    }
}