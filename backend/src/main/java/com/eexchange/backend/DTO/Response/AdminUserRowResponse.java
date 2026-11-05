package com.eexchange.backend.DTO.Response;

import com.eexchange.backend.Entity.Enums.UserStatus;

import java.time.Instant;

public record AdminUserRowResponse(
        Long id,
        String email,
        String name,
        CollegeSummary college,
        UserStatus status,
        Instant blockedUntil,
        Instant createdAt,
        Instant lastLoginAt,
        long postCount
) {
    public record CollegeSummary(Long id, String name) {
    }
}