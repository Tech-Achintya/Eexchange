package com.eexchange.backend.DTO;

import com.eexchange.backend.Entity.Enums.UserStatus;

import java.time.Instant;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        CollegeSummary college,
        Boolean isCollegeVerified,
        UserStatus status,
        Instant createdAt
) {
    public record CollegeSummary(Long id, String name) {
    }
}