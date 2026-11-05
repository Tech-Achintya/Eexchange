package com.eexchange.backend.DTO.Response;

import java.time.Instant;

public record InterestReceivedResponse(
        Long id,
        Long postId,
        String postTitle,
        String firstImageUrl,
        String senderName,
        String message,
        String contactInfo,
        Boolean isRead,
        Instant createdAt
) {
}