package com.eexchange.backend.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private Long userId;
    private String email;
    private String name;
    private Long collegeId;
    private String collegeName;
    private boolean isCollegeVerified;
    private boolean isNewUser;
    private String token;
}
