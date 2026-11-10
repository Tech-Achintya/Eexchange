package com.eexchange.backend.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data kept between "signup started" and "OTP verified".
 * <p>
 * Deliberately contains no password or other credential: the password is
 * supplied only by the client that successfully verified the OTP, so starting
 * a signup for someone else's email can never choose their password.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PendingSignupData {
    private String email;
    private Long collegeId;
    private boolean isCollegeVerified;
}
