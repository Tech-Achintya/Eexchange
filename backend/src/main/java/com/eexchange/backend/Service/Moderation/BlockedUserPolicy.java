package com.eexchange.backend.Service.Moderation;

import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;

import java.time.Instant;

/**
 * One rule for "the owner is currently blocked", shared by AI moderation and
 * the admin manual-review actions so they can never disagree.
 */
public final class BlockedUserPolicy {

    public static final String USER_BLOCKED_REASON = "USER_BLOCKED";

    private BlockedUserPolicy() {
    }

    // A TEMP block only counts while blockedUntil is in the future; an expired
    // block is inactive. This matches JWTfilter and login.
    public static boolean isEffectivelyBlocked(User user) {
        if (user == null) {
            return false;
        }
        if (user.getStatus() == UserStatus.PERM_BLOCKED) {
            return true;
        }
        if (user.getStatus() == UserStatus.TEMP_BLOCKED) {
            return user.getBlockedUntil() != null
                    && user.getBlockedUntil().isAfter(Instant.now());
        }
        return false;
    }
}
