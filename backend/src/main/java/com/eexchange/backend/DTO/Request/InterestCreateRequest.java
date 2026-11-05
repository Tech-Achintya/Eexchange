package com.eexchange.backend.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterestCreateRequest {

    @NotBlank(message = "Message is required")
    @Size(min = 5, max = 1000, message = "Message must be between 5 and 1000 characters")
    private String message;

    @NotBlank(message = "Contact info is required")
    @Size(min = 5, max = 255, message = "Contact info must be between 5 and 255 characters")
    private String contactInfo;
}