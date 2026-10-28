package com.eexchange.backend.DTO;
public record CategoryResponse(
        Long id,
        String name,
        String slug
) {}