package com.eexchange.backend.DTO.Response;

public record CollegeResponse(
        Long id,
        String name,
        String city,
        String state,
        Boolean isOthers
) {}