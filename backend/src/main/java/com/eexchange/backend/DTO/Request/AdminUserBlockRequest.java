package com.eexchange.backend.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class AdminUserBlockRequest {

    public enum Type {
        TEMP,
        PERM
    }

    @NotNull(message = "Block type is required")
    private Type type;

    private Instant until;

    @NotBlank(message = "Reason is required")
    @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
    private String reason;
}