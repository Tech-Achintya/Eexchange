package com.eexchange.backend.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminModerationRejectRequest {

    @NotBlank(message = "Reason is required")
    @Size(min = 5, max = 300, message = "Reason must be between 5 and 300 characters")
    private String reason;
}
