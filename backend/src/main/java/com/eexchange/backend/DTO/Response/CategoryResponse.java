package com.eexchange.backend.DTO.Response;
public record CategoryResponse(
        Long id,
        String name,
        String slug
) {}