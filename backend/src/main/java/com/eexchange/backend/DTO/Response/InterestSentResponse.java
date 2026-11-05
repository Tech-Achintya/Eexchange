package com.eexchange.backend.DTO.Response;

import com.eexchange.backend.Entity.Enums.PostStatus;

import java.time.Instant;

public record InterestSentResponse(
        Long id,
        Long postId,
        String postTitle,
        PostStatus postStatus,
        String message,
        Instant createdAt
) {
}