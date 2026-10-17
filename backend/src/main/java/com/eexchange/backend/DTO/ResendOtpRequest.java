package com.eexchange.backend.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResendOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Purpose is required")
    @Pattern(regexp = "^(signup|reset)$", message = "Purpose must be either 'signup' or 'reset'")
    private String purpose;
}
