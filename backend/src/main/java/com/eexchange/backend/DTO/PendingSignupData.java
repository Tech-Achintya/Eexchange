package com.eexchange.backend.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PendingSignupData {
    private String email;
    private String name;
    private Long collegeId;
    private boolean isCollegeVerified;
    private String passwordHash;
}
