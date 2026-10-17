package com.eexchange.backend.DTO;

public record CachedCollegeDomain(
        String domain,
        Long collegeId,
        String collegeName,
        boolean isActive
) {}
