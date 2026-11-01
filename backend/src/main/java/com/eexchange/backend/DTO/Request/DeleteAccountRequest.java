package com.eexchange.backend.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {
    @NotBlank(message = "Password is required")
    private String password;
}